package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class CommonStockAnalysisOriginalsTest {
  private static final String URL = "https://example.com/results/1";
  private static final LocalDate DATE = LocalDate.of(2026, 10, 23);
  private static final Instant AS_OF = Instant.parse("2026-10-26T00:00:00Z");
  private static final String QUOTE = "삼성전자 매출이 10% 증가했습니다.";

  @Test
  void verifiesOriginalEvidenceAndRecordsItsHash() {
    var analysis = analysis("매출이 10% 증가했어요.", QUOTE, DATE);
    var report = check(analysis, "삼성전자 실적", QUOTE);
    analysis.recordVerification(report);

    assertThat(analysis.getStatus()).isEqualTo(CommonStockAnalysis.Status.VERIFIED);
    assertThat(report.sources().getFirst().originalContentHash()).hasSize(64);
  }

  @Test
  void rejectsInventedNumbersEvenWithoutASpaceAfterKoreanWords() {
    var report = check(analysis("매출이20% 증가했어요.", QUOTE, DATE), "삼성전자 실적", QUOTE);
    assertThat(report.sources().getFirst().numbersMatched()).isFalse();
  }

  @Test
  void rejectsSignAndUnitMismatches() {
    var negative = "삼성전자 매출이 -10% 변동했습니다.";
    assertThat(
            check(analysis("매출이 10% 변동했어요.", negative, DATE), "삼성전자 실적", negative)
                .sources()
                .getFirst()
                .numbersMatched())
        .isFalse();
    var points = "삼성전자 이익률이 10%p 변동했습니다.";
    assertThat(
            check(analysis("이익률이 10% 변동했어요.", points, DATE), "삼성전자 실적", points)
                .sources()
                .getFirst()
                .numbersMatched())
        .isFalse();
    var amount = "삼성전자 매출은 10억원입니다.";
    assertThat(
            check(analysis("매출은 10조원이에요.", amount, DATE), "삼성전자 실적", amount)
                .sources()
                .getFirst()
                .numbersMatched())
        .isFalse();
  }

  @Test
  void rejectsAnotherCompanyAndAnInventedQuote() {
    String other = "다른회사 매출이 10% 증가했습니다.";
    assertThat(
            check(analysis("매출이 10% 증가했어요.", other, DATE), "다른회사 실적", other)
                .sources()
                .getFirst()
                .companyMatched())
        .isFalse();
    assertThat(
            check(analysis("매출이 늘었어요.", "삼성전자 영업이익이 증가했습니다.", DATE), "삼성전자 실적", QUOTE)
                .sources()
                .getFirst()
                .quoteMatched())
        .isFalse();
  }

  @Test
  void rejectsAClaimedPublicationDateThatDiffersFromTheOriginal() {
    assertThat(
            check(analysis("매출이 늘었어요.", QUOTE, DATE.minusDays(1)), "삼성전자 실적", QUOTE)
                .sources()
                .getFirst()
                .datesMatched())
        .isFalse();
  }

  @Test
  void onlyAllowsCurrencyConversionsCalculatedFromTheOriginal() {
    String quote = "삼성전자 revenue is KRW 10 billion.";
    assertThat(
            check(analysis("매출은 100억원이에요.", quote, DATE), "삼성전자 실적", quote)
                .sources()
                .getFirst()
                .numbersMatched())
        .isTrue();
    assertThat(
            check(analysis("매출은 10억원이에요.", quote, DATE), "삼성전자 실적", quote)
                .sources()
                .getFirst()
                .numbersMatched())
        .isFalse();
  }

  private CommonStockAnalysisVerification check(
      CommonStockAnalysis analysis, String title, String body) {
    return CommonStockAnalysisVerification.fromOriginals(
        asset(),
        analysis,
        Map.of(URL, new PortfolioAnalysisSourceDocument(title, body, DATE)),
        AS_OF.plusSeconds(2));
  }

  private Asset asset() {
    Asset asset = Asset.listedStock("삼성전자", "005930", StockMarket.KOSPI, null, DATE);
    ReflectionTestUtils.setField(asset, "id", 1L);
    return asset;
  }

  private CommonStockAnalysis analysis(String summary, String quote, LocalDate publication) {
    var content =
        new CommonStockAnalysisContent(
            ImpactDirection.POSITIVE,
            ImpactLevel.HIGH,
            summary,
            "발표를 확인해요.",
            "이후 변화를 확인해요.",
            "공식 발표를 참고했어요.",
            List.of(new Source("실적", URL, publication, null, "발표를 확인했어요.", quote)));
    return new CommonStockAnalysis(
        asset(),
        new CommonStockAnalysisKey(1L, "model", "v1", "e1", AS_OF),
        content,
        "a".repeat(64),
        AS_OF.plusSeconds(1));
  }
}
