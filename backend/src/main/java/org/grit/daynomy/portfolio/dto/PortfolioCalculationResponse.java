package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record PortfolioCalculationResponse(
    @Schema(
            description = "평가에 사용한 가격의 기준일 중 가장 이른 날짜. 모두 평가 불가하면 null",
            example = "2026-10-22",
            nullable = true)
        LocalDate baseDate,
    @Schema(
            description =
                "COMPLETE: 모든 종목 평가 가능·가격 기준일 동일, MIXED_DATES: 모든 종목 평가 가능·기준일 상이, PARTIAL: 가격 또는 환율 부족으로 일부 종목 평가 불가",
            example = "COMPLETE")
        PortfolioPricingStatus pricingStatus,
    @Schema(description = "평가 가능한 종목 수", example = "1") int pricedHoldingCount,
    @Schema(description = "가격 또는 환율 부족으로 평가할 수 없는 종목 수", example = "0") int unpricedHoldingCount,
    @Schema(description = "전체 매입금액(KRW)", example = "700000.00") BigDecimal totalPurchaseAmount,
    @Schema(
            description = "평가 가능한 종목만 합산한 평가금액(KRW). PARTIAL이면 전체 자산의 평가금액이 아님",
            example = "750000.00")
        BigDecimal totalEvaluationAmount,
    @Schema(
            description =
                "현재 보유 수량 기준 현재 평가와 직전 종가 평가의 차이(KRW). 갱신 대기 가격이 포함되거나 가격 종류·기준일이 다르면 null",
            example = "10000.00",
            nullable = true)
        BigDecimal dailyProfitLoss,
    @Schema(
            description = "직전 종가 평가금액 대비 등락률(%). 일별 평가손익을 계산할 수 없으면 null",
            example = "1.35",
            nullable = true)
        BigDecimal dailyReturnRate,
    @Schema(description = "평가손익(KRW). PARTIAL이면 null", example = "50000.00", nullable = true)
        BigDecimal totalProfitLoss,
    @Schema(description = "매입금액 대비 수익률(%). PARTIAL이면 null", example = "7.14", nullable = true)
        BigDecimal totalReturnRate,
    @Schema(description = "요청 순서의 종목별 평가 결과. 가격이 없는 종목도 포함")
        List<PortfolioHoldingResponse> holdings,
    @Schema(description = "평가 가능한 종목의 시장별 평가금액과 비중")
        List<PortfolioMarketAllocationResponse> marketAllocations) {}
