package org.grit.daynomy.portfolio.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioTransactionRequestRepository;
import org.grit.daynomy.portfolio.repository.PortfolioTransactionRequestRepository.StoredRequest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Successful keys are shared across V1/V2 and survive subsequent changes to the original trade. */
@Service
@RequiredArgsConstructor
public class PortfolioTransactionIdempotencyService {
  private static final Pattern KEY = Pattern.compile("[A-Za-z0-9._:-]{1,128}");
  private static final ObjectMapper JSON =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

  private final PortfolioTransactionRequestRepository requests;
  private final MemberRepository members;
  private final PlatformTransactionManager transactionManager;
  private final MeterRegistry metrics;

  public PortfolioTransactionResponse execute(
      Long memberId,
      String key,
      PortfolioTransactionRequest request,
      Supplier<Map<LocalDate, BigDecimal>> prepareRates,
      Function<Map<LocalDate, BigDecimal>, PortfolioTransactionResponse> write) {
    try {
      if (key == null || !KEY.matcher(key).matches()) {
        throw new BusinessException(PortfolioErrorCode.INVALID_IDEMPOTENCY_KEY);
      }
      String hash = fingerprint(request);
      var existing = requests.find(memberId, key);
      if (existing.isPresent()) return completed(replay(existing.orElseThrow(), hash), true);

      // External calls must remain outside the write transaction, as in the ledger locking ADR.
      var rates = prepareRates.get();
      TransactionTemplate transaction = new TransactionTemplate(transactionManager);
      transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
      Outcome outcome =
          transaction.execute(
              status -> {
                requests.limitLockWait();
                members
                    .findByIdForUpdate(memberId)
                    .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
                // A competing instance can have committed while rates were prepared or the lock
                // was awaited.
                var committed = requests.find(memberId, key);
                if (committed.isPresent())
                  return new Outcome(replay(committed.orElseThrow(), hash), true);
                PortfolioTransactionResponse response = write.apply(rates);
                requests.save(memberId, key, hash, encode(response));
                return new Outcome(response, false);
              });
      return completed(outcome.response(), outcome.replayed());
    } catch (PessimisticLockingFailureException | QueryTimeoutException exception) {
      count("busy");
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_TRANSACTION_BUSY);
    } catch (BusinessException exception) {
      count(
          exception.errorCode() == PortfolioErrorCode.IDEMPOTENCY_KEY_CONFLICT
              ? "conflict"
              : exception.errorCode() == PortfolioErrorCode.INVALID_IDEMPOTENCY_KEY
                  ? "invalid"
                  : "failed");
      throw exception;
    } catch (RuntimeException exception) {
      count("failed");
      throw exception;
    }
  }

  private PortfolioTransactionResponse completed(
      PortfolioTransactionResponse response, boolean replayed) {
    // Count only after the transaction actually committed.
    count(replayed ? "replayed" : "created");
    return response;
  }

  private void count(String outcome) {
    metrics.counter("daynomy.portfolio.transaction.idempotency", "outcome", outcome).increment();
  }

  private static PortfolioTransactionResponse replay(StoredRequest stored, String hash) {
    if (!stored.hash().equals(hash))
      throw new BusinessException(PortfolioErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    if (stored.version() != 1)
      throw new IllegalStateException("Unsupported stored trade response version");
    try {
      return JSON.readValue(stored.response(), PortfolioTransactionResponse.class);
    } catch (JsonProcessingException exception) {
      // Do not include persisted financial data in exception messages.
      throw new IllegalStateException("Stored trade response cannot be read");
    }
  }

  private static String encode(PortfolioTransactionResponse response) {
    try {
      return JSON.writeValueAsString(response);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Trade response cannot be stored");
    }
  }

  static String fingerprint(PortfolioTransactionRequest request) {
    String value =
        String.join(
            "|",
            "create-trade-v1",
            request.assetId().toString(),
            request.type().name(),
            request.tradeDate().toString(),
            request.quantity().toString(),
            number(request.unitPrice()),
            number(request.fee()),
            number(request.tax()),
            number(request.exchangeRate()),
            String.valueOf(request.tradeTime()));
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is unavailable");
    }
  }

  private static String number(BigDecimal value) {
    return value == null ? "null" : value.stripTrailingZeros().toPlainString();
  }

  private record Outcome(PortfolioTransactionResponse response, boolean replayed) {}
}
