package org.grit.daynomy.investmentcalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysis;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysisStatus;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventImpactLevel;

public record PortfolioEventAnalysisResponse(
    @Schema(
            description =
                "READY: 방향별 표본 3개 이상인 통계 존재, NO_PORTFOLIO: 가격이 있는 관련 보유자산 없음, INSUFFICIENT_DATA: 모든 방향의 표본 부족")
        PortfolioEventAnalysisStatus status,
    @Schema(description = "과거 반응의 영향 수준. HIGH: 1% 이상, MEDIUM: 0.5% 이상, LOW: 그 미만 또는 유효 통계 없음")
        PortfolioEventImpactLevel impactLevel,
    @Schema(description = "최신 종가가 있는 관련 보유자산 수", example = "2") int relatedAssetCount,
    @Schema(description = "관련 자산 평가금액 합계. 최신 종가 × 보유 수량이며 통화 환산 없이 합산")
        BigDecimal totalEvaluationAmount,
    @Schema(description = "평가에 사용한 관련 종목별 최신 종가 기준일 중 가장 오래된 날짜. 관련 자산이 없으면 null", nullable = true)
        LocalDate priceBaseDate,
    @Schema(description = "발표값 하락·동일·상승별 과거 반응 통계. 관련 자산이 없으면 빈 배열. 예상 수익률을 보장하지 않음")
        List<PortfolioReactionStatisticsResponse> historicalReactions) {

  public static PortfolioEventAnalysisResponse from(PortfolioEventAnalysis analysis) {
    return new PortfolioEventAnalysisResponse(
        analysis.status(),
        analysis.impactLevel(),
        analysis.relatedAssetCount(),
        analysis.totalEvaluationAmount(),
        analysis.priceBaseDate(),
        analysis.statistics().stream().map(PortfolioReactionStatisticsResponse::from).toList());
  }
}
