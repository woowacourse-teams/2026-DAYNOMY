package org.grit.daynomy.investmentcalendar.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysis;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventAnalysisStatus;
import org.grit.daynomy.investmentcalendar.domain.PortfolioEventImpactLevel;

public record PortfolioEventAnalysisResponse(
    PortfolioEventAnalysisStatus status,
    PortfolioEventImpactLevel impactLevel,
    int relatedAssetCount,
    BigDecimal totalEvaluationAmount,
    LocalDate priceBaseDate,
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
