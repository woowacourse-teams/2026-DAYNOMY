package org.grit.daynomy.emailsubscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;

public record AdminEmailAnalysisDetailResponse(
    AdminEmailAnalysisItemResponse analysis,
    String model,
    String analysisVersion,
    String evidenceVersion,
    Content content,
    @Schema(description = "원문 검증 전이면 null") Verification verification,
    String invalidationReason) {
  public static AdminEmailAnalysisDetailResponse from(CommonStockAnalysis analysis) {
    var content = analysis.getContent();
    var verification = analysis.getVerification();
    return new AdminEmailAnalysisDetailResponse(
        AdminEmailAnalysisItemResponse.from(analysis),
        analysis.getModel(),
        analysis.getAnalysisVersion(),
        analysis.getEvidenceVersion(),
        new Content(
            content.direction(),
            content.impactLevel(),
            content.issueSummary(),
            content.expectedReaction(),
            content.outlook(),
            content.reason(),
            content.sources().stream()
                .map(
                    source ->
                        new Source(
                            source.title(),
                            source.url(),
                            source.publishedOn(),
                            source.occurredOn(),
                            source.excerpt(),
                            source.factQuote()))
                .toList()),
        verification == null
            ? null
            : new Verification(
                verification.checkedAt(),
                verification.sources().stream()
                    .map(
                        check ->
                            new SourceCheck(
                                check.url(),
                                check.companyMatched(),
                                check.datesMatched(),
                                check.numbersMatched(),
                                check.quoteMatched(),
                                check.passed()))
                    .toList()),
        analysis.getInvalidationReason());
  }

  public record Content(
      ImpactDirection direction,
      ImpactLevel impactLevel,
      String issueSummary,
      String expectedReaction,
      String outlook,
      String reason,
      List<Source> sources) {}

  public record Source(
      String title,
      String url,
      LocalDate publishedOn,
      LocalDate occurredOn,
      String excerpt,
      String factQuote) {}

  public record Verification(Instant checkedAt, List<SourceCheck> sources) {}

  public record SourceCheck(
      String url,
      boolean companyMatched,
      boolean datesMatched,
      boolean numbersMatched,
      boolean quoteMatched,
      boolean passed) {}
}
