package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisEvidenceVerifierTest {
  private final PortfolioAnalysisDocumentClient documents =
      mock(PortfolioAnalysisDocumentClient.class);
  private final PortfolioAnalysisEvidenceVerifier verifier =
      new PortfolioAnalysisEvidenceVerifier(documents);
  private final LocalDate date = LocalDate.of(2022, 10, 26);
  private final PortfolioAnalysisResult.Source source =
      new PortfolioAnalysisResult.Source(
          "Results", "https://example.com/article", date, null, "수요가 감소했어요.", "Demand fell.");

  @Test
  void fetchesSharedOriginalOnceAndPreservesVerifiedFacts() {
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(
            Optional.of(new PortfolioAnalysisSourceDocument("2022 results", "Demand fell.", date)));
    var first = impact("삼성전자", List.of(source), 1);
    var second = impact("SK하이닉스", List.of(source), 2);
    var result =
        verifier.verify(new PortfolioAnalysisResult("", List.of(first, second), List.of(source)));
    assertThat(result.impacts()).containsExactly(first, second);
    verify(documents).fetch(eq(source.url()), any());
  }

  @Test
  void oneUnverifiedSourceInvalidatesWholeJudgmentButKeepsOtherAsset() {
    var unavailable =
        new PortfolioAnalysisResult.Source(
            "Missing", "https://example.com/missing", date, null, "", "Other fact.");
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(Optional.of(new PortfolioAnalysisSourceDocument("", "Demand fell.", date)));
    when(documents.fetch(eq(unavailable.url()), any())).thenReturn(Optional.empty());
    var result =
        verifier.verify(
            new PortfolioAnalysisResult(
                "Outdated overall",
                List.of(
                    impact("삼성전자", List.of(source, unavailable), 1),
                    impact("SK하이닉스", List.of(source), 2)),
                List.of(source, unavailable)));
    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::assetName)
        .containsExactly("SK하이닉스", "삼성전자");
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.NEGATIVE);
    assertThat(result.impacts().getLast().direction()).isNull();
    assertThat(result.impacts().getLast().sources()).isEmpty();
    assertThat(result.impacts())
        .extracting(PortfolioAnalysisResult.AssetImpactResult::sortOrder)
        .containsExactly(1, 2);
    assertThat(result.sources()).containsExactly(source);
    assertThat(result.overallImpact()).isEmpty();
  }

  @Test
  void replacesAiPublicationDateWithConfirmedOriginalDate() {
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(
            Optional.of(
                new PortfolioAnalysisSourceDocument("", "Demand fell.", LocalDate.of(2020, 1, 1))));
    var result =
        verifier.verify(
            new PortfolioAnalysisResult(
                "", List.of(impact("삼성전자", List.of(source), 1)), List.of(source)));
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.NEGATIVE);
    assertThat(result.sources().getFirst().publishedOn()).isEqualTo(LocalDate.of(2020, 1, 1));
  }

  @Test
  void keepsVerifiedFactAndClearsUnconfirmedEventDate() {
    var supplied =
        new PortfolioAnalysisResult.Source(
            source.title(), source.url(), date, date, source.excerpt(), source.factQuote());
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(
            Optional.of(new PortfolioAnalysisSourceDocument("Results", "Demand fell.", date)));
    var result =
        verifier.verify(
            new PortfolioAnalysisResult(
                "", List.of(impact("삼성전자", List.of(supplied), 1)), List.of(supplied)));
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.NEGATIVE);
    assertThat(result.sources().getFirst().publishedOn()).isEqualTo(date);
    assertThat(result.sources().getFirst().occurredOn()).isNull();
    assertThat(result.sources().getFirst().factQuote()).isEqualTo(source.factQuote());
  }

  @Test
  void missingPublicationMetadataDoesNotEraseConfirmedFactsOrInventDate() {
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(
            Optional.of(new PortfolioAnalysisSourceDocument("Results", "Demand fell.", null)));
    var result =
        verifier.verify(
            new PortfolioAnalysisResult(
                "", List.of(impact("삼성전자", List.of(source), 1)), List.of(source)));
    assertThat(result.impacts().getFirst().direction()).isEqualTo(ImpactDirection.NEGATIVE);
    assertThat(result.sources().getFirst().publishedOn()).isNull();
  }

  @Test
  void explicitFalseDateInExplanationStillRejectsJudgment() {
    var ordinary = impact("삼성전자", List.of(source), 1);
    var falseClaim =
        new PortfolioAnalysisResult.AssetImpactResult(
            ordinary.assetName(),
            ordinary.direction(),
            ordinary.impactLevel(),
            "2022-10-20에 수요가 감소했어요.",
            ordinary.expectedReaction(),
            ordinary.outlook(),
            ordinary.reason(),
            ordinary.evidenceSentence(),
            ordinary.sources(),
            ordinary.sortOrder());
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(
            Optional.of(
                new PortfolioAnalysisSourceDocument(
                    "Results", "October 26, 2022. Demand fell.", date)));
    var result =
        verifier.verify(new PortfolioAnalysisResult("", List.of(falseClaim), List.of(source)));
    assertThat(result.impacts().getFirst().direction()).isNull();
  }

  @Test
  void fetchesIndependentSourcesConcurrentlyWithoutRepeatingSharedUrl() throws Exception {
    var second =
        new PortfolioAnalysisResult.Source(
            "Other", "https://other.example.com/article", date, null, "수요가 감소했어요.", "Demand fell.");
    var started = new CountDownLatch(2);
    when(documents.fetch(any(), any()))
        .thenAnswer(
            invocation -> {
              started.countDown();
              if (!started.await(2, TimeUnit.SECONDS)) return Optional.empty();
              return Optional.of(new PortfolioAnalysisSourceDocument("", "Demand fell.", date));
            });
    var result =
        verifier.verify(
            new PortfolioAnalysisResult(
                "",
                List.of(
                    impact("삼성전자", List.of(source), 1),
                    impact("SK하이닉스", List.of(second, source), 2)),
                List.of(source, second)));
    assertThat(result.impacts()).allSatisfy(impact -> assertThat(impact.direction()).isNotNull());
    verify(documents).fetch(eq(source.url()), any());
    verify(documents).fetch(eq(second.url()), any());
  }

  private PortfolioAnalysisResult.AssetImpactResult impact(
      String name, List<PortfolioAnalysisResult.Source> sources, int order) {
    return new PortfolioAnalysisResult.AssetImpactResult(
        name,
        ImpactDirection.NEGATIVE,
        ImpactLevel.HIGH,
        "수요가 감소했어요.",
        "비용 부담이 커질 수 있어요.",
        "수요 회복을 확인해야 해요.",
        "수요가 감소했어요.",
        "수요가 감소했어요.",
        sources,
        order);
  }
}
