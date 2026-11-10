package org.grit.daynomy.investmentcalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventDirection;
import org.grit.daynomy.investmentcalendar.domain.PortfolioReactionStatistics;

public record PortfolioReactionStatisticsResponse(
    @Schema(description = "과거 일정의 발표값 변화 방향(DECREASED, UNCHANGED, INCREASED)")
        InvestmentEventDirection direction,
    @Schema(description = "이 방향에서 수익률을 계산할 수 있었던 과거 일정 수", example = "3") int sampleCount,
    @Schema(description = "과거 포트폴리오 반응 수익률 중앙값(%). 최신 평가금액으로 가중하며 표본 3개 미만이면 null", nullable = true)
        BigDecimal medianReturnRate,
    @Schema(description = "과거 반응 수익률의 하위 25% 값(%). 표본 3개 미만이면 null", nullable = true)
        BigDecimal lowerReturnRate,
    @Schema(description = "과거 반응 수익률의 상위 75% 위치 값(%). 표본 3개 미만이면 null", nullable = true)
        BigDecimal upperReturnRate,
    @Schema(description = "하위 수익률을 적용한 예상 손익 금액. 평가금액과 같은 단위. 표본 3개 미만이면 null", nullable = true)
        BigDecimal lowerEstimatedAmount,
    @Schema(description = "상위 수익률을 적용한 예상 손익 금액. 평가금액과 같은 단위. 표본 3개 미만이면 null", nullable = true)
        BigDecimal upperEstimatedAmount) {

  public static PortfolioReactionStatisticsResponse from(PortfolioReactionStatistics statistics) {
    return new PortfolioReactionStatisticsResponse(
        statistics.direction(),
        statistics.sampleCount(),
        statistics.medianReturnRate(),
        statistics.lowerReturnRate(),
        statistics.upperReturnRate(),
        statistics.lowerEstimatedAmount(),
        statistics.upperEstimatedAmount());
  }
}
