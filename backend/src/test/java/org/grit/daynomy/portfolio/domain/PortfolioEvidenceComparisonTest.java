package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioEvidenceComparison.Fact;
import org.grit.daynomy.portfolio.domain.PortfolioEvidenceComparison.Result;
import org.junit.jupiter.api.Test;

class PortfolioEvidenceComparisonTest {
  @Test
  void emptyEvidenceIsUnconfirmedEvenWhenPreviousIsEmpty() {
    assertThat(PortfolioEvidenceComparison.compare(List.of(), List.of()))
        .isEqualTo(Result.UNCONFIRMED);
  }

  private final Fact original =
      new Fact("https://example.invalid/old", LocalDate.of(2026, 9, 25), "영업이익이 감소했어요.");

  @Test
  void unchangedQuotePreservesJudgmentDespiteParaphrasedSummary() {
    var old =
        new Fact(
            original.url(),
            original.occurredOn(),
            "영업이익이 줄었어요.",
            "Operating profit decreased by 10%.");
    var current =
        new Fact(
            original.url(),
            original.occurredOn(),
            "영업이익이 10% 감소했어요.",
            "Operating profit decreased by 10%.");
    assertThat(PortfolioEvidenceComparison.compare(List.of(old), List.of(current)))
        .isEqualTo(Result.SAME);
  }

  @Test
  void changedQuoteIsNotHiddenByUnchangedSummary() {
    var old =
        new Fact(
            original.url(), original.occurredOn(), original.excerpt(), "Profit decreased by 10%.");
    var corrected =
        new Fact(
            original.url(), original.occurredOn(), original.excerpt(), "Profit increased by 10%.");
    assertThat(PortfolioEvidenceComparison.compare(List.of(old), List.of(corrected)))
        .isEqualTo(Result.UNCONFIRMED);
  }

  @Test
  void reprintedFactDoesNotBecomeNewEvidence() {
    assertThat(
            PortfolioEvidenceComparison.compare(
                List.of(original),
                List.of(
                    new Fact(
                        "https://example.invalid/reprint",
                        original.occurredOn(),
                        " 영업이익이  감소했어요. "))))
        .isEqualTo(Result.SAME);
  }

  @Test
  void newDatedEventAllowsRevision() {
    assertThat(
            PortfolioEvidenceComparison.compare(
                List.of(original),
                List.of(
                    new Fact(
                        "https://example.invalid/new", LocalDate.of(2026, 10, 1), "새 계약을 체결했어요."))))
        .isEqualTo(Result.NEW_EVIDENCE);
  }

  @Test
  void missingEvidenceOrUnclearEventMustNotChangeJudgment() {
    for (List<Fact> facts :
        List.of(
            List.<Fact>of(),
            List.of(new Fact(original.url(), original.occurredOn(), "이익이 늘었어요.")),
            List.of(new Fact("https://example.invalid/new", null, "새 계약을 체결했어요.")),
            List.of(
                new Fact("https://example.invalid/new", original.occurredOn(), "새 계약을 체결했어요.")))) {
      assertThat(PortfolioEvidenceComparison.compare(List.of(original), facts))
          .isEqualTo(Result.UNCONFIRMED);
    }
  }

  @Test
  void unknownDateRequiresSameSourceAndFactButIgnoresTrackingParameters() {
    var old = new Fact("https://example.invalid/report?id=1", null, "계약을 체결했어요.");
    assertThat(
            PortfolioEvidenceComparison.compare(
                List.of(old),
                List.of(
                    new Fact(
                        "https://example.invalid/report?utm_source=search&id=1#section",
                        null,
                        old.excerpt()))))
        .isEqualTo(Result.SAME);
    assertThat(
            PortfolioEvidenceComparison.compare(
                List.of(old),
                List.of(new Fact("https://example.invalid/other", null, old.excerpt()))))
        .isEqualTo(Result.UNCONFIRMED);
  }
}
