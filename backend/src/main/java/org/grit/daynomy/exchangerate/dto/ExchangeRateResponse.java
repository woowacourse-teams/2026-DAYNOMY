package org.grit.daynomy.exchangerate.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.exchangerate.domain.UsdKrwRate;

public record ExchangeRateResponse(
    LocalDate requestedDate,
    LocalDate baseDate,
    BigDecimal rate,
    String baseCurrency,
    String quoteCurrency) {
  public static ExchangeRateResponse from(UsdKrwRate rate) {
    return new ExchangeRateResponse(
        rate.getRequestedDate(), rate.getBaseDate(), rate.getRate(), "USD", "KRW");
  }
}
