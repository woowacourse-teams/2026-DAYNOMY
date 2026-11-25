package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionRequest;
import org.grit.daynomy.portfolio.dto.PortfolioTransactionResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioTransactionRequestRepository;
import org.grit.daynomy.portfolio.repository.PortfolioTransactionRequestRepository.StoredRequest;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class PortfolioTransactionIdempotencyServiceTest {
  private final PortfolioTransactionRequestRepository requests =
      mock(PortfolioTransactionRequestRepository.class);
  private final MemberRepository members = mock(MemberRepository.class);
  private final PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
  private final SimpleMeterRegistry metrics = new SimpleMeterRegistry();
  private final PortfolioTransactionIdempotencyService service =
      new PortfolioTransactionIdempotencyService(
          requests, members, transactions, metrics, new PortfolioTransactionTimings(metrics));

  @Test
  void newRequestPassesTheLockedMemberAndCountsSuccessAfterCommit() {
    Member locked = mock(Member.class);
    TransactionStatus status = mock(TransactionStatus.class);
    when(requests.find(1L, "new-key")).thenReturn(Optional.empty());
    when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(locked));
    when(transactions.getTransaction(any())).thenReturn(status);
    var response = new PortfolioTransactionResponse(42L, null, BigDecimal.ZERO);
    doAnswer(
            invocation -> {
              assertThat(
                      metrics
                          .counter(
                              "daynomy.portfolio.transaction.idempotency", "outcome", "created")
                          .count())
                  .isZero();
              return null;
            })
        .when(transactions)
        .commit(status);

    var actual =
        service.execute(
            PortfolioTransactionTimings.Api.V2,
            1L,
            "new-key",
            request(),
            Map::of,
            (member, rates) -> {
              assertThat(member).isSameAs(locked);
              verify(members).findByIdForUpdate(1L);
              verify(requests, times(2)).find(1L, "new-key");
              verify(transactions, never()).commit(status);
              return response;
            });

    assertThat(actual).isSameAs(response);
    verify(members, times(1)).findByIdForUpdate(1L);
    verify(requests).save(eq(1L), eq("new-key"), anyString(), anyString());
    verify(transactions).commit(status);
    assertThat(
            metrics
                .counter("daynomy.portfolio.transaction.idempotency", "outcome", "created")
                .count())
        .isEqualTo(1);
  }

  @Test
  void completedRequestSurvivesUnavailableExternalRatesAndDoesNotStartATransaction()
      throws Exception {
    var request = request();
    var original = new PortfolioTransactionResponse(42L, null, new BigDecimal("12.34"));
    when(requests.find(1L, "retry"))
        .thenReturn(
            Optional.of(
                new StoredRequest(
                    PortfolioTransactionIdempotencyService.fingerprint(request),
                    1,
                    new ObjectMapper().writeValueAsString(original))));
    var actual =
        service.execute(
            PortfolioTransactionTimings.Api.V1,
            1L,
            "retry",
            request,
            () -> {
              throw new AssertionError("Completed requests must not fetch rates");
            },
            (member, rates) -> {
              throw new AssertionError("Completed requests must not replay the ledger");
            });
    assertThat(actual).isEqualTo(original);
    verifyNoInteractions(members, transactions);
    assertThat(
            metrics
                .get("daynomy.portfolio.transaction.idempotency")
                .tag("outcome", "replayed")
                .counter()
                .count())
        .isEqualTo(1);
  }

  @Test
  void unreadableOrUnsupportedStoredResultNeverReexecutesTheTrade() {
    String hash = PortfolioTransactionIdempotencyService.fingerprint(request());
    for (StoredRequest bad :
        List.of(new StoredRequest(hash, 1, "broken"), new StoredRequest(hash, 2, "{}"))) {
      when(requests.find(1L, "retry")).thenReturn(Optional.of(bad));
      assertThatThrownBy(
              () ->
                  service.execute(
                      PortfolioTransactionTimings.Api.V1,
                      1L,
                      "retry",
                      request(),
                      () -> {
                        throw new AssertionError("No external calls");
                      },
                      (member, rates) -> {
                        throw new AssertionError("No duplicate effects");
                      }))
          .isInstanceOf(IllegalStateException.class);
    }
    verifyNoInteractions(members, transactions);
  }

  @Test
  void allTradeInputsIncludingTimeAndNullRateParticipateInTheFingerprint() {
    var r = request();
    String hash = PortfolioTransactionIdempotencyService.fingerprint(r);
    List<PortfolioTransactionRequest> changes =
        List.of(
            new PortfolioTransactionRequest(
                2L,
                r.type(),
                r.tradeDate(),
                r.quantity(),
                r.unitPrice(),
                r.fee(),
                r.tax(),
                r.exchangeRate(),
                r.tradeTime()),
            new PortfolioTransactionRequest(
                r.assetId(),
                PortfolioTransactionType.SELL,
                r.tradeDate(),
                r.quantity(),
                r.unitPrice(),
                r.fee(),
                r.tax(),
                r.exchangeRate(),
                r.tradeTime()),
            new PortfolioTransactionRequest(
                r.assetId(),
                r.type(),
                r.tradeDate().plusDays(1),
                r.quantity(),
                r.unitPrice(),
                r.fee(),
                r.tax(),
                r.exchangeRate(),
                r.tradeTime()),
            new PortfolioTransactionRequest(
                r.assetId(),
                r.type(),
                r.tradeDate(),
                2L,
                r.unitPrice(),
                r.fee(),
                r.tax(),
                r.exchangeRate(),
                r.tradeTime()),
            new PortfolioTransactionRequest(
                r.assetId(),
                r.type(),
                r.tradeDate(),
                r.quantity(),
                BigDecimal.TEN,
                r.fee(),
                r.tax(),
                r.exchangeRate(),
                r.tradeTime()),
            new PortfolioTransactionRequest(
                r.assetId(),
                r.type(),
                r.tradeDate(),
                r.quantity(),
                r.unitPrice(),
                BigDecimal.ONE,
                r.tax(),
                r.exchangeRate(),
                r.tradeTime()),
            new PortfolioTransactionRequest(
                r.assetId(),
                r.type(),
                r.tradeDate(),
                r.quantity(),
                r.unitPrice(),
                r.fee(),
                BigDecimal.ONE,
                r.exchangeRate(),
                r.tradeTime()),
            new PortfolioTransactionRequest(
                r.assetId(),
                r.type(),
                r.tradeDate(),
                r.quantity(),
                r.unitPrice(),
                r.fee(),
                r.tax(),
                null,
                r.tradeTime()),
            new PortfolioTransactionRequest(
                r.assetId(),
                r.type(),
                r.tradeDate(),
                r.quantity(),
                r.unitPrice(),
                r.fee(),
                r.tax(),
                r.exchangeRate(),
                LocalTime.of(9, 1)));
    assertThat(changes.stream().map(PortfolioTransactionIdempotencyService::fingerprint))
        .doesNotContain(hash)
        .doesNotHaveDuplicates();
  }

  @Test
  void invalidKeysNeverAccessTheDatabase() {
    for (String key : List.of("", "bad key", "a".repeat(129))) {
      assertThatThrownBy(
              () ->
                  service.execute(
                      PortfolioTransactionTimings.Api.V1, 1L, key, request(), null, null))
          .isInstanceOfSatisfying(
              BusinessException.class,
              e -> assertThat(e.errorCode()).isEqualTo(PortfolioErrorCode.INVALID_IDEMPOTENCY_KEY));
    }
    verifyNoInteractions(requests, members, transactions);
  }

  private PortfolioTransactionRequest request() {
    return new PortfolioTransactionRequest(
        1L,
        PortfolioTransactionType.BUY,
        LocalDate.of(2026, 11, 20),
        1L,
        new BigDecimal("100.00"),
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ONE,
        LocalTime.of(9, 0));
  }
}
