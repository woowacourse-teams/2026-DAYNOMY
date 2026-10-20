package org.grit.daynomy.exchangerate.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.exchangerate.ExchangeRateErrorCode;
import org.grit.daynomy.exchangerate.domain.UsdKrwRate;
import org.grit.daynomy.exchangerate.dto.ExchangeRateResponse;
import org.grit.daynomy.exchangerate.repository.UsdKrwRateRepository;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.grit.daynomy.external.eodhd.dto.EodhdDailyPrice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.NEVER)
public class ExchangeRateService {
  public static final LocalDate SUPPORTED_FROM = LocalDate.of(2000, 1, 1);
  private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
  private static final Duration CACHE_TTL = Duration.ofHours(6);
  private final UsdKrwRateRepository repository;
  private final EodhdClient client;
  private final Clock exchangeRateClock;
  private final Map<LocalDate, Instant> unavailableUntil = new ConcurrentHashMap<>();

  public ExchangeRateResponse quote(LocalDate requestedDate) {
    return loadQuote(requestedDate, false);
  }

  public ExchangeRateResponse refreshToday() {
    return loadQuote(LocalDate.now(exchangeRateClock.withZone(ZoneId.of("Asia/Seoul"))), true);
  }

  private ExchangeRateResponse loadQuote(LocalDate requestedDate, boolean refresh) {
    Instant now = exchangeRateClock.instant();
    if (requestedDate == null
        || requestedDate.isBefore(SUPPORTED_FROM)
        || requestedDate.isAfter(
            LocalDate.now(exchangeRateClock.withZone(ZoneId.of("Asia/Seoul")))))
      throw new BusinessException(ExchangeRateErrorCode.INVALID_EXCHANGE_RATE_DATE);
    LocalDate cutoff = LocalDate.now(exchangeRateClock.withZone(NEW_YORK)).minusDays(1);
    LocalDate through = requestedDate.isBefore(cutoff) ? requestedDate : cutoff;
    var cached = repository.findById(requestedDate);
    if (!refresh && cached.isPresent()) {
      UsdKrwRate rate = cached.get();
      LocalDate fetchedCutoff = rate.getFetchedAt().atZone(NEW_YORK).toLocalDate().minusDays(1);
      if (requestedDate.isBefore(fetchedCutoff)
          || rate.getFetchedAt().isAfter(now.minus(CACHE_TTL)))
        return ExchangeRateResponse.from(rate);
    }
    if (!refresh && unavailableUntil.getOrDefault(requestedDate, Instant.MIN).isAfter(now))
      throw new BusinessException(ExchangeRateErrorCode.EXCHANGE_RATE_UNAVAILABLE);
    var latest =
        client.getUsdKrwRates(through.minusDays(14), through).stream()
            .filter(
                r ->
                    r.date() != null
                        && !r.date().isAfter(through)
                        && !r.date().isBefore(through.minusDays(14))
                        && r.close() != null
                        && r.close().signum() > 0)
            .max(Comparator.comparing(EodhdDailyPrice::date));
    if (latest.isEmpty()) {
      unavailableUntil.entrySet().removeIf(entry -> !entry.getValue().isAfter(now));
      if (unavailableUntil.size() >= 1024) unavailableUntil.clear();
      unavailableUntil.put(requestedDate, now.plus(CACHE_TTL));
      throw new BusinessException(ExchangeRateErrorCode.EXCHANGE_RATE_UNAVAILABLE);
    }
    var row = latest.orElseThrow();
    var rate = new UsdKrwRate(requestedDate, row.date(), row.close(), now);
    // No outer transaction: only this atomic write holds a connection during persistence.
    repository.upsert(requestedDate, rate.getBaseDate(), rate.getRate(), now);
    unavailableUntil.remove(requestedDate);
    return ExchangeRateResponse.from(rate);
  }

  public Map<LocalDate, BigDecimal> ratesForDates(Collection<LocalDate> dates) {
    Map<LocalDate, BigDecimal> rates = new HashMap<>();
    for (LocalDate date : new TreeSet<>(dates)) {
      try {
        rates.put(date, quote(date).rate());
      } catch (BusinessException exception) {
        if (exception.errorCode() != ExchangeRateErrorCode.EXCHANGE_RATE_UNAVAILABLE)
          throw exception;
      }
    }
    return Map.copyOf(rates);
  }

  public static BigDecimal priceInKrwOrNull(
      StockDailyPrice price, Map<LocalDate, BigDecimal> rates) {
    if (!"USD".equals(price.getAsset().getCurrency())) return price.getClosePrice();
    BigDecimal rate = rates.get(price.getBaseDate());
    return rate == null ? null : price.getClosePrice().multiply(rate);
  }
}
