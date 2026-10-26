package org.grit.daynomy.emailsubscription.domain;

import static org.assertj.core.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;
import org.junit.jupiter.api.Test;

class EmailStockDigestTest {
  private final LocalDate date = LocalDate.of(2026, 10, 23);

  @Test
  void firstNoticeIsExplicitAndUncertainStocksAreBlocked() {
    var current = digest(2L, date, "매출 증가", "매출 10% 증가", ImpactDirection.POSITIVE);
    assertThat(current.newInformation(Map.of(), Set.of()).body()).contains("처음 안내");
    assertThat(current.newInformation(Map.of(), Set.of(1L)).stocks()).isEmpty();
  }

  @Test
  void changedAnalysisIdWordingAndSourceUrlDoNotRepeatTheSameFactsAndPriceDate() {
    var previous = digest(1L, date, "매출 증가", "매출 10% 증가", ImpactDirection.POSITIVE);
    var current = digest(2L, date, "실적이 개선됐어요", "매출 10% 증가", ImpactDirection.POSITIVE);
    assertThat(current.newInformation(Map.of(1L, previous), Set.of()).stocks()).isEmpty();
  }

  @Test
  void newPriceDayIsReportedWithoutPretendingTheAnalysisChanged() {
    var previous = digest(1L, date, "매출 증가", "매출 10% 증가", ImpactDirection.POSITIVE);
    var current = digest(2L, date.plusDays(3), "매출 증가", "매출 10% 증가", ImpactDirection.NEGATIVE);
    assertThat(current.newInformation(Map.of(1L, previous), Set.of()).body())
        .contains("새 가격 기준일")
        .doesNotContain("새 근거에 따른 분석 변화");
  }

  @Test
  void changedJudgmentRequiresNewEvidenceEvenWhenTheUrlIsUnchanged() {
    var previous = digest(1L, date, "매출 증가", "매출 10% 증가", ImpactDirection.POSITIVE);
    var unsupported = digest(2L, date, "전망 악화", "매출 10% 증가", ImpactDirection.NEGATIVE);
    assertThat(unsupported.newInformation(Map.of(1L, previous), Set.of()).stocks()).isEmpty();
    var current = digest(2L, date, "전망 악화", "매출 20% 감소", ImpactDirection.NEGATIVE);
    assertThat(current.newInformation(Map.of(1L, previous), Set.of()).body())
        .contains("새 근거에 따른 분석 변화");
  }

  @Test
  void anotherMembersHistoryDoesNotBecomeMyPreviousAnalysis() {
    var current = digest(2L, date, "매출 증가", "매출 10% 증가", ImpactDirection.POSITIVE);
    assertThat(current.newInformation(Map.of(99L, current), Set.of()).body()).contains("처음 안내");
  }

  @Test
  void eventAnalysisWithMissingPricesDoesNotInventReturnsOrRepeatUnchangedEvidence() {
    var before = digest(1L, date, "실적", "매출 증가", ImpactDirection.POSITIVE);
    var content = before.stocks().getFirst().content();
    var current =
        new EmailStockDigest(
            date.plusDays(1),
            date,
            List.of(new EmailStockDigest.Stock(2L, 1L, "종목", null, null, null, content)));
    assertThat(current.newInformation(Map.of(), Set.of()).body())
        .contains("가격 정보 미확인")
        .doesNotContain("등락률:");
    assertThat(current.newInformation(Map.of(1L, before), Set.of()).stocks()).isEmpty();
  }

  private EmailStockDigest digest(
      Long id, LocalDate base, String summary, String quote, ImpactDirection direction) {
    var content =
        new CommonStockAnalysisContent(
            direction,
            ImpactLevel.HIGH,
            summary,
            "",
            "",
            "근거",
            List.of(new Source("실적", "https://example.invalid/" + id, date, date, quote, quote)));
    return new EmailStockDigest(
        base,
        base.minusDays(1),
        List.of(
            new EmailStockDigest.Stock(
                id, 1L, "종목", BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE, content)));
  }
}
