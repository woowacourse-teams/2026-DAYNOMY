package org.grit.daynomy.portfolio.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.junit.jupiter.api.BeforeEach;
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

  @BeforeEach
  void fetchesThroughTheSharedDocumentLoader() {
    when(documents.fetchAll(org.mockito.ArgumentMatchers.anyList())).thenCallRealMethod();
  }

  @Test
  void doesNotTreatEarningsScheduleAsPreparedBusinessEvidence() {
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(
            Optional.of(
                new PortfolioAnalysisSourceDocument(
                    "카카오 결산실적공시 예고", "결산실적 공시예정일 2026-11-05. 안내.", date)));
    var evidence = verifier.prepare(Map.of("카카오", List.of(source.url())), List.of(source));
    assertThat(evidence.references(List.of(new PortfolioAnalysisTarget("카카오")))).isEmpty();
  }

  @Test
  void retriesUnavailableSelectedOriginalBeforeReplacingItWithOtherPages() {
    String unrelated = "https://example.com/recruitment";
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(Optional.empty())
        .thenReturn(
            Optional.of(new PortfolioAnalysisSourceDocument("SK하이닉스 실적", "영업이익이 증가했어요.", date)));
    when(documents.fetch(eq(unrelated), any()))
        .thenReturn(
            Optional.of(new PortfolioAnalysisSourceDocument("", "SK하이닉스 채용 행사에 참여하세요.", null)));
    var evidence =
        verifier.prepare(
            Map.of("SK하이닉스", List.of(source.url())),
            List.of(source, new PortfolioAnalysisResult.Source("채용", unrelated)));
    assertThat(evidence.input(List.of(new PortfolioAnalysisTarget("SK하이닉스"))).toString())
        .contains("영업이익이 증가했어요.")
        .doesNotContain("채용 행사");
    verify(documents, times(2)).fetch(eq(source.url()), any());
    verify(documents, never()).fetch(eq(unrelated), any());
  }

  @Test
  void doesNotTreatUndatedUnselectedRecruitmentPageAsPreparedFinancialEvidence() {
    String unrelated = "https://example.com/recruitment";
    when(documents.fetch(eq(source.url()), any())).thenReturn(Optional.empty());
    when(documents.fetch(eq(unrelated), any()))
        .thenReturn(
            Optional.of(new PortfolioAnalysisSourceDocument("", "SK하이닉스 채용 행사 안내예요.", null)));
    var evidence =
        verifier.prepare(
            Map.of("SK하이닉스", List.of(source.url())),
            List.of(source, new PortfolioAnalysisResult.Source("채용", unrelated)));
    assertThat(evidence.references(List.of(new PortfolioAnalysisTarget("SK하이닉스")))).isEmpty();
  }

  @Test
  void doesNotFetchDisclosureListingAsIndividualEvidence() {
    String listing = "https://www.k5.co.kr/stock/035420/official_notice";
    var evidence =
        verifier.prepare(
            Map.of("네이버", List.of(listing)),
            List.of(new PortfolioAnalysisResult.Source("NAVER 공시 목록", listing)));
    assertThat(evidence.references(List.of(new PortfolioAnalysisTarget("네이버")))).isEmpty();
    verify(documents, never()).fetch(any(), any());
  }

  @Test
  void doesNotAssociateArticleThroughAnotherCompanyShareButtonOrFooter() {
    var button = "https://example.com/samsung-results";
    var footer = "https://example.com/samsung-outlook";
    when(documents.fetch(eq(button), any()))
        .thenReturn(
            Optional.of(
                new PortfolioAnalysisSourceDocument(
                    "삼성전자 실적 발표", "카카오톡 공유. 삼성전자가 실적을 발표했어요.", date)));
    when(documents.fetch(eq(footer), any()))
        .thenReturn(
            Optional.of(
                new PortfolioAnalysisSourceDocument(
                    "삼성전자 실적 전망", "삼성전자 실적 전망. " + "본문 내용. ".repeat(100) + "카카오 공유", date)));
    var evidence =
        verifier.prepare(
            Map.of("카카오", List.of(), "삼성전자", List.of(button, footer)),
            List.of(
                new PortfolioAnalysisResult.Source("Results", button),
                new PortfolioAnalysisResult.Source("Outlook", footer)));
    assertThat(evidence.references(List.of(new PortfolioAnalysisTarget("카카오")))).isEmpty();
    assertThat(evidence.references(List.of(new PortfolioAnalysisTarget("삼성전자")))).isNotEmpty();
  }

  @Test
  void doesNotInferCompanyFromAnUnselectedDocumentEvenWhenItsOpeningMentionsTheCompany() {
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(
            Optional.of(
                new PortfolioAnalysisSourceDocument(
                    "PowerPoint 프레젠테이션",
                    "Q4 2025 and Annual Earnings Results\n2026. 2. 12 | Kakao Investor Relations\nRevenue grew.",
                    date)));
    var evidence = verifier.prepare(Map.of("카카오", List.of()), List.of(source));
    assertThat(evidence.references(List.of(new PortfolioAnalysisTarget("카카오")))).isEmpty();
  }

  @Test
  void usesSelectedOriginalWithoutAddingUnselectedOlderReports() {
    String old = "https://example.com/previous-report";
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(
            Optional.of(new PortfolioAnalysisSourceDocument("삼성전자 실적", "현재 확인된 사실.", date)));
    when(documents.fetch(eq(old), any()))
        .thenReturn(
            Optional.of(new PortfolioAnalysisSourceDocument("삼성전자 과거 전망", "이전 전망 자료.", date)));
    var evidence =
        verifier.prepare(
            Map.of("삼성전자", List.of(source.url())),
            List.of(source, new PortfolioAnalysisResult.Source("삼성전자 과거 전망", old)));
    assertThat(evidence.input(List.of(new PortfolioAnalysisTarget("삼성전자"))).toString())
        .contains("현재 확인된 사실.")
        .doesNotContain("이전 전망 자료.");
  }

  @Test
  void excludesJavascriptStubEvenWhenAiSelectedIt() {
    when(documents.fetch(eq(source.url()), any()))
        .thenReturn(
            Optional.of(
                new PortfolioAnalysisSourceDocument(
                    "카카오 영입", "You need to enable JavaScript to run this app.", null)));
    var evidence = verifier.prepare(Map.of("카카오", List.of(source.url())), List.of(source));
    assertThat(evidence.references(List.of(new PortfolioAnalysisTarget("카카오")))).isEmpty();
  }

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
  void verifiesCombinedQuarterClaimsAgainstAllSelectedOriginals() {
    var first =
        new PortfolioAnalysisResult.Source(
            "Q2 results",
            "https://example.com/q2",
            date,
            null,
            "2022년 2분기 매출이 증가했어요.",
            "Q2 2022 revenue increased.");
    var second =
        new PortfolioAnalysisResult.Source(
            "Q3 results",
            "https://example.com/q3",
            date,
            null,
            "2022년 3분기 수요가 감소했어요.",
            "Q3 2022 demand fell.");
    when(documents.fetch(eq(first.url()), any()))
        .thenReturn(
            Optional.of(
                new PortfolioAnalysisSourceDocument(first.title(), first.factQuote(), date)));
    when(documents.fetch(eq(second.url()), any()))
        .thenReturn(
            Optional.of(
                new PortfolioAnalysisSourceDocument(second.title(), second.factQuote(), date)));
    var impact =
        new PortfolioAnalysisResult.AssetImpactResult(
            "삼성전자",
            ImpactDirection.NEGATIVE,
            ImpactLevel.HIGH,
            "2022년 2분기 성장 이후 3분기 수요가 감소했어요.",
            "비용 부담이 커질 수 있어요.",
            "수요 회복을 확인해야 해요.",
            "2022년 3분기 수요가 감소했어요.",
            "2022년 2분기 매출이 증가했어요.",
            List.of(first, second),
            1);
    var verified =
        verifier.verify(new PortfolioAnalysisResult("", List.of(impact), impact.sources()));
    assertThat(verified.impacts().getFirst().direction()).isEqualTo(ImpactDirection.NEGATIVE);
    var falseClaim =
        new PortfolioAnalysisResult.AssetImpactResult(
            impact.assetName(),
            impact.direction(),
            impact.impactLevel(),
            "2022년 4분기 수요가 감소했어요.",
            impact.expectedReaction(),
            impact.outlook(),
            impact.reason(),
            impact.evidenceSentence(),
            impact.sources(),
            1);
    var rejected =
        verifier.verify(new PortfolioAnalysisResult("", List.of(falseClaim), impact.sources()));
    assertThat(rejected.impacts().getFirst().direction()).isNull();
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
