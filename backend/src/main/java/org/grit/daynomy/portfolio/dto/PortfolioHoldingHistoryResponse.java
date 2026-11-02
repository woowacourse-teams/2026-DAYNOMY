package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;

public record PortfolioHoldingHistoryResponse(
    @Schema(description = "변경 이력 ID. BUY·SELL 이력은 거래 수정·삭제의 transactionId로 사용", example = "1")
        Long historyId,
    @Schema(description = "종목 ID", example = "1") Long assetId,
    @Schema(description = "종목 코드", example = "005930") String assetCode,
    @Schema(description = "종목명", example = "삼성전자") String name,
    @Schema(
            description =
                "변경 유형. BUY: 매수, SELL: 매도, OPENING_BALANCE: 초기 잔고, ADJUSTED: 잔고 조정, REMOVED: 보유자산 삭제, ADDED·UPDATED: 이전 방식의 등록·수정",
            example = "BUY")
        PortfolioHoldingChangeType changeType,
    @Schema(
            description = "이력에 기록된 보유 수량. 매수·매도 이력은 거래 생성 당시 보유 수량이며 거래 수량은 transactionQuantity 참조",
            example = "0")
        long quantity,
    @Schema(description = "이력에 기록된 원화(KRW) 평균 매입가. 매수·매도 이력은 거래 생성 당시 값", example = "0.00")
        BigDecimal averagePurchasePrice,
    @Schema(description = "이력이 적용되는 날짜. 거래는 거래일, 잔고 조정·삭제는 서울 기준 처리일", example = "2026-10-22")
        LocalDate effectiveDate,
    @Schema(description = "매수·매도 거래 수량(주). 잔고 조정·삭제 이력은 null", example = "10", nullable = true)
        Long transactionQuantity,
    @Schema(
            description = "종목 통화(currency) 기준 거래 단가. 잔고 조정·삭제 이력은 null",
            example = "70000.0000",
            nullable = true)
        BigDecimal unitPrice,
    @Schema(description = "종목 통화(currency) 기준 수수료. 잔고 조정·삭제는 0", example = "0.00") BigDecimal fee,
    @Schema(description = "종목 통화(currency) 기준 세금. 잔고 조정·삭제는 0", example = "0.00") BigDecimal tax,
    @Schema(description = "이력 생성 시각(UTC). 거래일과 다를 수 있음", example = "2026-10-22T03:00:00Z")
        Instant occurredAt,
    @Schema(description = "종목 통화. KRW 또는 USD", example = "KRW") String currency,
    @Schema(description = "거래에 적용한 환율(1 USD당 KRW). 국내 거래는 1", example = "1")
        BigDecimal exchangeRate,
    @Schema(description = "거래 시각 (Asia/Seoul), 미기록 시 null", example = "09:35", nullable = true)
        @com.fasterxml.jackson.annotation.JsonFormat(pattern = "HH:mm")
        LocalTime tradeTime) {
  @Schema(description = "평균 매입가의 통화. 항상 KRW", example = "KRW")
  @com.fasterxml.jackson.annotation.JsonProperty("averagePurchasePriceCurrency")
  public String averagePurchasePriceCurrency() {
    return "KRW";
  }

  public static PortfolioHoldingHistoryResponse from(PortfolioHoldingHistory history) {
    return new PortfolioHoldingHistoryResponse(
        history.getId(),
        history.getAsset().getId(),
        history.getAsset().getAssetCode(),
        history.getAsset().getDisplayName(),
        history.getChangeType(),
        history.getQuantity(),
        history.getAveragePurchasePrice(),
        history.getEffectiveDate(),
        history.getTransactionQuantity(),
        history.getUnitPrice(),
        history.getFee(),
        history.getTax(),
        history.getCreatedAt(),
        history.getAsset().getCurrency(),
        history.getExchangeRate(),
        history.getTradeTime());
  }
}
