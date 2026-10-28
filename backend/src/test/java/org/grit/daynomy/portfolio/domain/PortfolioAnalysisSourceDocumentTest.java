package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisSourceDocumentTest {
  @Test
  void providesExactKoreanWonConversionsBeforeAiWritesExplanations() {
    String quote =
        "Reports revenues of 79.3187 trillion won and operating profit of KRW 520.3 billion.";
    var original = new PortfolioAnalysisSourceDocument("Results", quote, null);
    assertThat(original.verifiedWonAmounts(quote))
        .containsEntry("79.3187 trillion won", "793187억원")
        .containsEntry("KRW 520.3 billion", "5203억원");
    assertThat(original.verifiedWonAmounts("Reports revenues of 80 trillion won.")).isEmpty();
    assertThat(
            new PortfolioAnalysisSourceDocument(
                    "Annual Report",
                    "Please wait for a moment. Main Documents Select Audit Report.",
                    null)
                .isAnnouncementOnly())
        .isTrue();
  }

  @Test
  void distinguishesEarningsScheduleFromPublishedResults() {
    assertThat(
            new PortfolioAnalysisSourceDocument(
                    "public-disclosure-view",
                    "Please follow the link below to see Samsung Electronics' 2026 2Q Earnings Release 2026 2Q conference PDF.",
                    null)
                .isAnnouncementOnly())
        .isTrue();
    assertThat(
            new PortfolioAnalysisSourceDocument("", "카카오 결산실적공시 예고. 결산실적 공시예정일 안내.", null)
                .isAnnouncementOnly())
        .isTrue();
    assertThat(
            new PortfolioAnalysisSourceDocument("카카오 실적 발표", "매출은 2조원, 영업이익은 2030억원이다.", null)
                .isAnnouncementOnly())
        .isFalse();
  }

  @Test
  void convertsExplicitEnglishBillionWonToHundredMillionWonWithoutChangingTheOriginal() {
    String quote = "NAVER reported operating profit of KRW 520.3 billion.";
    var original = new PortfolioAnalysisSourceDocument("NAVER results", quote, null);
    assertThat(original.normalizeAmountUnit(quote, "영업이익은 520.3억원이에요."))
        .isEqualTo("영업이익은 5203억원이에요.");
    assertThat(original.normalizeAmountUnit(quote, "영업이익은 5203억원이에요."))
        .isEqualTo("영업이익은 5203억원이에요.");
    assertThat(original.body()).isEqualTo(quote);
  }

  private final LocalDate date = LocalDate.of(2022, 10, 26);
  private final String quote = "Memory demand weakened.";
  private final PortfolioAnalysisSourceDocument document =
      new PortfolioAnalysisSourceDocument(
          "Third quarter 2022 results", "October 26, 2022. Memory demand weakened.", date);

  @Test
  void acceptsOriginalQuoteAndConfirmedDates() {
    assertThat(
            document.rejectionReason("Memory\n demand weakened.", date, date, "2022년 실적이 악화됐어요."))
        .isNull();
  }

  @Test
  void rejectsChangedNumbersOrNegation() {
    assertThat(document.rejectionReason("Memory demand did not weaken.", date, null, ""))
        .isNotNull();
  }

  @Test
  void rejectsOldArticlePresentedAsCurrentResults() {
    assertThat(document.rejectionReason(quote, LocalDate.of(2026, 10, 20), null, "2026년 실적이에요."))
        .isNotNull();
    assertThat(document.rejectionReason(quote, date, null, "2026년 실적이에요.")).isNotNull();
  }

  @Test
  void publicationDateDoesNotProveEventDate() {
    var withoutEventDate = new PortfolioAnalysisSourceDocument("Results", quote, date);
    assertThat(withoutEventDate.rejectionReason(quote, date, date, "")).isNotNull();
    assertThat(withoutEventDate.rejectionReason(quote, date, null, "")).isNull();
  }

  @Test
  void unknownAndFutureDatesAreNotAccepted() {
    assertThat(
            new PortfolioAnalysisSourceDocument("", quote, null)
                .rejectionReason(quote, date, null, ""))
        .isNotNull();
    var future = LocalDate.now(java.time.ZoneId.of("Asia/Seoul")).plusDays(1);
    assertThat(
            new PortfolioAnalysisSourceDocument("", quote, future)
                .rejectionReason(quote, future, null, ""))
        .isNotNull();
    assertThat(document.rejectionReason(quote, null, null, "")).isNull();
  }

  @Test
  void rejectsDifferentQuarterAndPreservesNumericSymbols() {
    assertThat(document.rejectionReason(quote, date, null, "2022년 3분기 실적이에요.")).isNull();
    assertThat(document.rejectionReason(quote, date, null, "2022년 2분기 실적이에요."))
        .isEqualTo("PERIOD_UNVERIFIED");
    assertThat(document.rejectionReason(quote, date, null, "2022년 하반기 실적이에요."))
        .isEqualTo("PERIOD_UNVERIFIED");
    var numeric = new PortfolioAnalysisSourceDocument("", "Revenue is 10⁶ dollars.", date);
    assertThat(numeric.rejectionReason("Revenue is 106 dollars.", date, null, ""))
        .isEqualTo("QUOTE_NOT_IN_ARTICLE");
  }

  @Test
  void acceptsEnglishHyphenatedQuarterAndHalfWithoutAcceptingDifferentPeriods() {
    String fact = "Third-quarter 2026 revenue increased in the first-half 2026 comparison.";
    var original = new PortfolioAnalysisSourceDocument("Third-Quarter 2026 results", fact, date);
    assertThat(original.rejectionReason(fact, date, null, "2026년 3분기 매출이 증가했어요.")).isNull();
    assertThat(original.rejectionReason(fact, date, null, "2026년 상반기와 비교했어요.")).isNull();
    assertThat(original.rejectionReason(fact, date, null, "2026년 2분기 실적이에요."))
        .isEqualTo("PERIOD_UNVERIFIED");
    assertThat(original.rejectionReason(fact, date, null, "2026년 하반기 실적이에요."))
        .isEqualTo("PERIOD_UNVERIFIED");
  }

  @Test
  void koreanParticleSpacingDoesNotDiscardSameNumbersAndFacts() {
    var original =
        new PortfolioAnalysisSourceDocument(
            "", "2026년 2분기 매출은 3조 3,888억 원을 기록했다.", LocalDate.of(2026, 8, 7));
    assertThat(
            original.rejectionReason(
                "2026년 2분기 매출은 3조 3,888억 원 을 기록했다.",
                original.publishedOn(),
                null,
                "2026년 2분기 실적이에요."))
        .isNull();
    assertThat(
            original.rejectionReason(
                "2026년 2분기 매출은 3조 3,889억 원 을 기록했다.", original.publishedOn(), null, ""))
        .isEqualTo("QUOTE_NOT_IN_ARTICLE");
    var english =
        new PortfolioAnalysisSourceDocument("", "The company is not able to expand.", date);
    assertThat(english.rejectionReason("The company is notable to expand.", date, null, ""))
        .isEqualTo("QUOTE_NOT_IN_ARTICLE");
    var separated = new PortfolioAnalysisSourceDocument("", "Revenue 10 0 dollars.", date);
    assertThat(separated.rejectionReason("Revenue 100 dollars.", date, null, ""))
        .isEqualTo("QUOTE_NOT_IN_ARTICLE");
  }

  @Test
  void acceptsThreeQNotationWithoutAcceptingDifferentQuarter() {
    var original =
        new PortfolioAnalysisSourceDocument(
            "3Q results", "2026. Earnings increased in 3Q.", LocalDate.of(2026, 10, 8));
    assertThat(
            original.rejectionReason(
                "Earnings increased in 3Q.", original.publishedOn(), null, "2026년 3분기 실적이에요."))
        .isNull();
    assertThat(
            original.rejectionReason(
                "Earnings increased in 3Q.", original.publishedOn(), null, "2026년 2분기 실적이에요."))
        .isEqualTo("PERIOD_UNVERIFIED");
  }

  @Test
  void resolvesNextYearOnlyFromAnUnambiguousDateWrittenInTheOriginal() {
    String fact = "Covers three years from next year through 2029.";
    var dated = new PortfolioAnalysisSourceDocument("Supply contract", "2026.09.22. " + fact, null);
    assertThat(dated.rejectionReason(fact, null, null, "2027년부터 2029년까지 공급해요.")).isNull();
    assertThat(dated.rejectionReason(fact, null, null, "2028년부터 공급해요."))
        .isEqualTo("PERIOD_UNVERIFIED");
    var rolling =
        new PortfolioAnalysisSourceDocument(
            "Outlook", "2026.09.22. Revenue may grow over the next year.", null);
    assertThat(
            rolling.rejectionReason(
                "Revenue may grow over the next year.", null, null, "2027년 매출이 늘어날 수 있어요."))
        .isEqualTo("PERIOD_UNVERIFIED");
    var metadataOnly =
        new PortfolioAnalysisSourceDocument("Supply contract", fact, LocalDate.of(2026, 9, 22));
    assertThat(
            metadataOnly.rejectionReason(fact, metadataOnly.publishedOn(), null, "2027년부터 공급해요."))
        .isEqualTo("PERIOD_UNVERIFIED");
    var ambiguous =
        new PortfolioAnalysisSourceDocument(
            "Supply contract", "2026.09.22. Comparison: 2025.09.22. " + fact, null);
    assertThat(ambiguous.rejectionReason(fact, null, null, "2027년부터 공급해요."))
        .isEqualTo("PERIOD_UNVERIFIED");
  }

  @Test
  void acceptsCompactQuarterAndYearNotationButRejectsDifferentQuarter() {
    var original =
        new PortfolioAnalysisSourceDocument(
            "SK hynix Announces 2Q26 Financial Results",
            "July 29, 2026. Revenue increased.",
            LocalDate.of(2026, 7, 29));
    assertThat(
            original.rejectionReason(
                "Revenue increased.", original.publishedOn(), null, "2026년 2분기 실적이에요."))
        .isNull();
    assertThat(
            original.rejectionReason(
                "Revenue increased.", original.publishedOn(), null, "2026년 3분기 실적이에요."))
        .isEqualTo("PERIOD_UNVERIFIED");
  }

  @Test
  void compactYearInTitleSupportsQuoteEvenWhenPublicationLineIsOutsideOpeningContext() {
    var original =
        new PortfolioAnalysisSourceDocument(
            "SK hynix Announces 2Q26 Financial Results",
            quote + " " + "Background. ".repeat(100),
            null);
    assertThat(original.rejectionReason(quote, null, null, "2026년 2분기 실적이에요.")).isNull();
    assertThat(original.rejectionReason(quote, null, null, "2025년 2분기 실적이에요."))
        .isEqualTo("PERIOD_UNVERIFIED");
  }

  @Test
  void abbreviatedKoreanForecastYearsSupportSameFullYearsButNotDifferentYear() {
    String forecast = "27년 매출 0.8조원, 28년 2.4조원, 29년 매출 3.9조원을 예상한다.";
    var original = new PortfolioAnalysisSourceDocument("NAVER 사업 전망", forecast, null);
    assertThat(
            original.rejectionReason(
                forecast, null, null, "2027년 매출 0.8조원, 2028년 2.4조원, 2029년 3.9조원을 예상했어요."))
        .isNull();
    assertThat(original.rejectionReason(forecast, null, null, "2030년 매출을 예상했어요."))
        .isEqualTo("PERIOD_UNVERIFIED");
  }

  @Test
  void bulkQuoteAmountsKeepTableUnitsExplicitCurrenciesAndPercentageExclusions() {
    String explicit = "Revenue reached KRW 2 billion.";
    String table = "Operating Profit 86 218 203 9% 3%";
    String plain = "Demand remained stable.";
    var original =
        new PortfolioAnalysisSourceDocument(
            "Results", explicit + "\n(in bn KRW)\n" + table + "\n" + plain, null);
    var amounts =
        original.verifiedWonAmounts(
            java.util.List.of(explicit, table, plain, "Unverified KRW 9 billion."));
    assertThat(amounts).hasSize(2);
    assertThat(amounts.get(explicit))
        .containsEntry("KRW 2 billion", "20억원")
        .containsEntry("2 billion KRW", "20억원");
    assertThat(amounts.get(table))
        .containsExactlyInAnyOrderEntriesOf(
            java.util.Map.of(
                "86 billion KRW",
                "860억원",
                "218 billion KRW",
                "2180억원",
                "203 billion KRW",
                "2030억원"));
  }

  @Test
  void translatesExplicitBillionWonTableUnitWithoutChangingAlreadyConvertedAmounts() {
    String table = "Total Revenue 1,953 2,081 2,133. Operating Profit 86 218 203.";
    var original = new PortfolioAnalysisSourceDocument("Results", "(in bn KRW) " + table, null);
    assertThat(original.normalizeAmountUnit(table, "총매출은 2,133억원, 영업이익은 203억원이에요."))
        .isEqualTo("총매출은 21330억원, 영업이익은 2030억원이에요.");
    assertThat(original.normalizeAmountUnit(table, "총매출은 2133억원이에요."))
        .isEqualTo("총매출은 21330억원이에요.");
    assertThat(original.normalizeAmountUnit(table, "총매출은 2조 1,330억원, 영업이익은 2030억원이에요."))
        .isEqualTo("총매출은 2조 1,330억원, 영업이익은 2030억원이에요.");
    assertThat(original.normalizeAmountUnit(table, "총매출은 2조1,330억원이에요."))
        .isEqualTo("총매출은 2조1,330억원이에요.");
    assertThat(
            new PortfolioAnalysisSourceDocument("Results", table, null)
                .normalizeAmountUnit(table, "영업이익은 203억원이에요."))
        .isEqualTo("영업이익은 203억원이에요.");
  }

  @Test
  void fourDigitAmountsAreNotInterpretedAsEventYears() {
    String fact = "Operating Profit 203 billion KRW.";
    var original = new PortfolioAnalysisSourceDocument("Results", fact, null);
    assertThat(original.rejectionReason(fact, null, null, "영업이익 2030억원을 기록했어요.")).isNull();
    assertThat(original.rejectionReason(fact, null, null, "영업이익 2030.5억원을 기록했어요.")).isNull();
    assertThat(original.rejectionReason(fact, null, null, "2030년 실적이에요."))
        .isEqualTo("PERIOD_UNVERIFIED");
  }
}
