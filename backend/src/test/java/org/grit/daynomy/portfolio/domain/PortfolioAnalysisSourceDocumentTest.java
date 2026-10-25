package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisSourceDocumentTest {
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
    var future = LocalDate.now().plusDays(1);
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
}
