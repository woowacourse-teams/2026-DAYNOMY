package org.grit.daynomy.exchangerate.service;

import java.math.BigDecimal;
import java.time.*;
import java.util.Comparator;
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
public class ExchangeRateService {
  private final UsdKrwRateRepository repository;
  private final EodhdClient client;

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ExchangeRateResponse quote(LocalDate requestedDate) {
    return loadQuote(requestedDate, false);
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public ExchangeRateResponse refreshToday() {
    return loadQuote(LocalDate.now(ZoneId.of("Asia/Seoul")), true);
  }

  private ExchangeRateResponse loadQuote(LocalDate requestedDate, boolean refresh) {
    if (requestedDate == null || requestedDate.isAfter(LocalDate.now(ZoneId.of("Asia/Seoul"))))
      throw new BusinessException(ExchangeRateErrorCode.INVALID_EXCHANGE_RATE_DATE);
    LocalDate cutoff = LocalDate.now(ZoneId.of("America/New_York")).minusDays(1);
    LocalDate through = requestedDate.isBefore(cutoff) ? requestedDate : cutoff;
    var cached = repository.findById(requestedDate);
    if (!refresh
        && cached.isPresent()
        && (requestedDate.isBefore(cutoff)
            || cached.get().getFetchedAt().isAfter(Instant.now().minus(Duration.ofHours(6)))))
      return ExchangeRateResponse.from(cached.get());
    var rows = client.getUsdKrwRates(through.minusDays(14), through);
    var latest =
        rows.stream()
            .filter(
                r ->
                    r.date() != null
                        && !r.date().isAfter(through)
                        && !r.date().isBefore(through.minusDays(14))
                        && r.close() != null
                        && r.close().signum() > 0)
            .max(Comparator.comparing(EodhdDailyPrice::date))
            .orElseThrow(
                () -> new BusinessException(ExchangeRateErrorCode.EXCHANGE_RATE_UNAVAILABLE));
    return ExchangeRateResponse.from(
        repository.save(new UsdKrwRate(requestedDate, latest.date(), latest.close())));
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public BigDecimal priceInKrwOrNull(StockDailyPrice price) {
    if (!"USD".equals(price.getAsset().getCurrency())) return price.getClosePrice();
    try {
      return price.getClosePrice().multiply(quote(price.getBaseDate()).rate());
    } catch (BusinessException unavailable) {
      return null;
    }
  }
}
