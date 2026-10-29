package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.portfolio.domain.PortfolioTransactionType;

public record PortfolioTransactionRequest(
    @NotNull @Positive @Schema(description = "거래할 종목 ID. 1 이상의 정수", example = "1") Long assetId,
    @NotNull @Schema(description = "거래 구분. BUY: 매수, SELL: 매도", example = "BUY")
        PortfolioTransactionType type,
    @NotNull
        @PastOrPresent
        @Schema(
            description = "거래일(yyyy-MM-dd). 미래 날짜 불가, 해당 종목의 마지막 보유 수량 등록·조정일 이후(당일 포함)",
            example = "2026-10-22")
        LocalDate tradeDate,
    @NotNull @Positive @Schema(description = "거래 수량(주). 1 이상의 정수", example = "10") Long quantity,
    @NotNull
        @DecimalMin("0.0001")
        @Digits(integer = 15, fraction = 4)
        @Schema(
            description = "종목 통화 기준 주당 거래 단가(KRW 또는 USD). 0.0001 이상, 정수 15자리·소수 4자리 이하",
            example = "70000.0000")
        BigDecimal unitPrice,
    @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 15, fraction = 2)
        @Schema(
            description = "종목 통화 기준 거래 수수료(KRW 또는 USD). 0 이상, 정수 15자리·소수 2자리 이하. 없으면 0 입력",
            example = "0.00")
        BigDecimal fee,
    @NotNull
        @DecimalMin("0.00")
        @Digits(integer = 15, fraction = 2)
        @Schema(
            description = "종목 통화 기준 거래 세금(KRW 또는 USD). 0 이상, 정수 15자리·소수 2자리 이하. 없으면 0 입력",
            example = "0.00")
        BigDecimal tax,
    @DecimalMin("0.000001")
        @Digits(integer = 10, fraction = 6)
        @Schema(
            description =
                "거래 적용 환율(1 USD당 KRW). 미국 종목은 필수이며 0.000001 이상, 정수 10자리·소수 6자리 이하. 국내 종목은 생략 또는 1",
            example = "1",
            nullable = true)
        BigDecimal exchangeRate) {
  public PortfolioTransactionRequest(
      Long assetId,
      PortfolioTransactionType type,
      LocalDate tradeDate,
      Long quantity,
      BigDecimal unitPrice,
      BigDecimal fee,
      BigDecimal tax) {
    this(assetId, type, tradeDate, quantity, unitPrice, fee, tax, null);
  }
}
