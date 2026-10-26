package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis.Status;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisVerification.SourceCheck;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;

class CommonStockAnalysisTest {

  private static final Instant AS_OF = Instant.parse("2026-10-19T00:00:00Z");
  private static final Instant ANALYZED_AT = AS_OF.plusSeconds(60);
  private static final Instant CHECKED_AT = ANALYZED_AT.plusSeconds(60);
  private static final String URL = "https://example.com/releases/earnings-2026";
  private static final String HASH = "a".repeat(64);
  private static final String CONTENT_HASH = "b".repeat(64);

  @Test
  void invalidationPreservesEvidenceAndFirstReasonAndCannotBeReverified() {
    var analysis = analysis(List.of(source(URL)));
    var report = verification(List.of(passed(URL)));
    analysis.recordVerification(report);
    analysis.invalidate("원문 정정", CHECKED_AT.plusSeconds(1));
    analysis.invalidate("다른 사유", CHECKED_AT.plusSeconds(2));
    analysis.recordVerification(report);
    assertThat(analysis.getStatus()).isEqualTo(Status.INVALIDATED);
    assertThat(analysis.getInvalidationReason()).isEqualTo("원문 정정");
    assertThat(analysis.getInvalidatedAt()).isEqualTo(CHECKED_AT.plusSeconds(1));
    assertThat(analysis.getVerification()).isEqualTo(report);
    assertThatThrownBy(() -> analysis.recordVerification(verification(List.of())))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void invalidationRejectsMissingReasonAndImpossibleTime() {
    var analysis = analysis(List.of(source(URL)));
    assertThatThrownBy(() -> analysis.invalidate(" ", CHECKED_AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> analysis.invalidate("x".repeat(501), CHECKED_AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> analysis.invalidate("오류", AS_OF))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(analysis.getStatus()).isEqualTo(Status.PENDING);
  }

  @Test
  void startsPendingEvenWhenTheAiIncludesSources() {
    CommonStockAnalysis analysis = analysis(List.of(source(URL)));

    assertThat(analysis.getStatus()).isEqualTo(Status.PENDING);
    assertThat(analysis.getVerification()).isNull();
  }

  @Test
  void acceptsOnlyCompleteServerVerificationAndKeepsItsEvidence() {
    CommonStockAnalysis analysis = analysis(List.of(source(URL)));
    var verification = verification(List.of(passed(URL)));

    analysis.recordVerification(verification);
    analysis.recordVerification(verification);

    assertThat(analysis.getStatus()).isEqualTo(Status.VERIFIED);
    assertThat(analysis.getVerification()).isEqualTo(verification);
  }

  @ParameterizedTest
  @MethodSource("failedChecks")
  void rejectsAnyFailedOrIncompleteOriginalSourceCheck(SourceCheck check) {
    CommonStockAnalysis analysis = analysis(List.of(source(URL)));

    analysis.recordVerification(verification(List.of(check)));

    assertThat(analysis.getStatus()).isEqualTo(Status.REJECTED);
  }

  static List<SourceCheck> failedChecks() {
    return List.of(
        new SourceCheck(URL, HASH, false, true, true, true),
        new SourceCheck(URL, HASH, true, false, true, true),
        new SourceCheck(URL, HASH, true, true, false, true),
        new SourceCheck(URL, HASH, true, true, true, false),
        new SourceCheck(URL, null, true, true, true, true),
        new SourceCheck(URL, "not-a-content-hash", true, true, true, true));
  }

  @Test
  void cannotUseChecksFromAnotherSourceOrOmitAnySource() {
    CommonStockAnalysis wrongSource = analysis(List.of(source(URL)));
    wrongSource.recordVerification(verification(List.of(passed(URL + "-other"))));
    CommonStockAnalysis omitted = analysis(List.of(source(URL), source(URL + "-second")));
    omitted.recordVerification(verification(List.of(passed(URL))));
    CommonStockAnalysis duplicate = analysis(List.of(source(URL)));
    duplicate.recordVerification(verification(List.of(passed(URL), passed(URL))));

    assertThat(wrongSource.getStatus()).isEqualTo(Status.REJECTED);
    assertThat(omitted.getStatus()).isEqualTo(Status.REJECTED);
    assertThat(duplicate.getStatus()).isEqualTo(Status.REJECTED);
  }

  @Test
  void rejectsInsufficientEvidenceEvenWhenAllChecksClaimToPass() {
    CommonStockAnalysis empty = analysis(List.of());
    empty.recordVerification(verification(List.of()));
    CommonStockAnalysis index = analysis(List.of(source("https://example.com/news")));
    index.recordVerification(verification(List.of(passed("https://example.com/news"))));

    assertThat(empty.getStatus()).isEqualTo(Status.REJECTED);
    assertThat(index.getStatus()).isEqualTo(Status.REJECTED);
  }

  @Test
  void rejectsAnUnknownJudgmentRatherThanTreatingItAsNoChange() {
    var content = new CommonStockAnalysisContent(null, null, "근거 부족", "", "", "근거 부족", List.of());
    CommonStockAnalysis analysis =
        new CommonStockAnalysis(asset(), key(), content, CONTENT_HASH, ANALYZED_AT);

    analysis.recordVerification(verification(List.of()));

    assertThat(analysis.getStatus()).isEqualTo(Status.REJECTED);
  }

  @Test
  void rejectsEvidenceDatedAfterTheAnalysisCutoff() {
    var future =
        new Source("실적", URL, LocalDate.of(2026, 10, 20), null, "매출이 늘었어요.", "Revenue increased.");
    CommonStockAnalysis analysis = analysis(List.of(future));

    analysis.recordVerification(verification(List.of(passed(URL))));

    assertThat(analysis.getStatus()).isEqualTo(Status.REJECTED);
  }

  @Test
  void doesNotOverwriteACompletedVerificationWithADifferentReport() {
    CommonStockAnalysis analysis = analysis(List.of(source(URL)));
    analysis.recordVerification(verification(List.of(passed(URL))));

    assertThatThrownBy(() -> analysis.recordVerification(verification(List.of())))
        .isInstanceOf(IllegalStateException.class);
    assertThat(analysis.getStatus()).isEqualTo(Status.VERIFIED);
  }

  @Test
  void rejectsVerificationBeforeAnalysisCompletion() {
    CommonStockAnalysis analysis = analysis(List.of(source(URL)));

    assertThatThrownBy(
            () ->
                analysis.recordVerification(
                    new CommonStockAnalysisVerification(CONTENT_HASH, AS_OF, List.of(passed(URL)))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(analysis.getStatus()).isEqualTo(Status.PENDING);
  }

  @Test
  void rejectsMismatchedAssetIdentityAndFutureCutoffs() {
    var differentAsset = new CommonStockAnalysisKey(2L, "model", "v1", "e1", AS_OF);
    var future = new CommonStockAnalysisKey(1L, "model", "v1", "e1", ANALYZED_AT.plusSeconds(1));

    assertThatThrownBy(
            () ->
                new CommonStockAnalysis(
                    asset(), differentAsset, content(List.of()), CONTENT_HASH, ANALYZED_AT))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new CommonStockAnalysis(
                    asset(), future, content(List.of()), CONTENT_HASH, ANALYZED_AT))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void normalizesCutoffPrecisionToTheDatabasePrecision() {
    var key = new CommonStockAnalysisKey(1L, "model", "v1", "e1", AS_OF.plusNanos(123456789));

    assertThat(key.asOf()).isEqualTo(AS_OF.plusNanos(123456000));
  }

  @Test
  void aVerificationForDifferentContentCannotChangeThePendingAnalysis() {
    CommonStockAnalysis analysis = analysis(List.of(source(URL)));
    var report =
        new CommonStockAnalysisVerification("c".repeat(64), CHECKED_AT, List.of(passed(URL)));

    assertThatThrownBy(() -> analysis.recordVerification(report))
        .isInstanceOf(IllegalArgumentException.class);
    assertThat(analysis.getStatus()).isEqualTo(Status.PENDING);
    assertThat(analysis.getVerification()).isNull();
  }

  private CommonStockAnalysis analysis(List<Source> sources) {
    return new CommonStockAnalysis(asset(), key(), content(sources), CONTENT_HASH, ANALYZED_AT);
  }

  private CommonStockAnalysisContent content(List<Source> sources) {
    return new CommonStockAnalysisContent(
        ImpactDirection.POSITIVE,
        ImpactLevel.HIGH,
        "매출이 늘었어요.",
        "사업에 유리해요.",
        "다음 발표를 확인해요.",
        "실적 발표를 근거로 해요.",
        sources);
  }

  private CommonStockAnalysisKey key() {
    return new CommonStockAnalysisKey(1L, "model", "v1", "e1", AS_OF);
  }

  private Asset asset() {
    Asset asset =
        Asset.listedStock(
            "삼성전자", "005930", StockMarket.KOSPI, "KR7005930003", LocalDate.of(2026, 10, 16));
    ReflectionTestUtils.setField(asset, "id", 1L);
    return asset;
  }

  private Source source(String url) {
    return new Source(
        "실적", url, LocalDate.of(2026, 10, 16), null, "매출이 늘었어요.", "Revenue increased.");
  }

  private SourceCheck passed(String url) {
    return new SourceCheck(url, HASH, true, true, true, true);
  }

  private CommonStockAnalysisVerification verification(List<SourceCheck> checks) {
    return new CommonStockAnalysisVerification(CONTENT_HASH, CHECKED_AT, checks);
  }
}
