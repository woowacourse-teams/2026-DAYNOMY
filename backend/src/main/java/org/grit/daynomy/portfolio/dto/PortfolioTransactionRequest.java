package org.grit.daynomy.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;

public record PortfolioTransactionRequest(
    @NotNull @Positive Long assetId,
    @NotNull PortfolioTransactionType type,
    @NotNull @PastOrPresent LocalDate tradeDate,
    @NotNull @Positive Long quantity,
    @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
    @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal fee,
    @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal tax,
    @DecimalMin("0.000001") @Digits(integer = 10, fraction = 6) BigDecimal exchangeRate,
    @io.swagger.v3.oas.annotations.media.Schema(
            description = "거래 시각 (Asia/Seoul), 생략 시 미기록",
            example = "09:35")
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm")
        LocalTime tradeTime) {
  public PortfolioTransactionRequest(
      Long assetId,
      PortfolioTransactionType type,
      LocalDate tradeDate,
      Long quantity,
      BigDecimal unitPrice,
      BigDecimal fee,
      BigDecimal tax,
      BigDecimal exchangeRate) {
    this(assetId, type, tradeDate, quantity, unitPrice, fee, tax, exchangeRate, null);
  }

  public PortfolioTransactionRequest(
      Long assetId,
      PortfolioTransactionType type,
      LocalDate tradeDate,
      Long quantity,
      BigDecimal unitPrice,
      BigDecimal fee,
      BigDecimal tax) {
    this(assetId, type, tradeDate, quantity, unitPrice, fee, tax, null, null);
  }
}
