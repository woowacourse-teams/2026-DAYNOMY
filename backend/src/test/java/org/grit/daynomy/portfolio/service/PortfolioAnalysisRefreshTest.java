package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisCheck.Outcome;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisSourceResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisRefreshTest {
  @Test
  void changesOnlyAssetWithNewEvidenceAndRebuildsSummaryFromPreservedJudgments() {
    var old =
        response(
            impact("삼성전자", false, ImpactDirection.NEGATIVE),
            impact("SK하이닉스", false, ImpactDirection.NEGATIVE));
    var candidate =
        response(
            impact("삼성전자", true, ImpactDirection.POSITIVE),
            impact("SK하이닉스", false, ImpactDirection.POSITIVE));
    var revision = PortfolioAnalysisRefresh.compare(old, candidate);
    assertThat(revision.outcome()).isEqualTo(Outcome.UPDATED);
    assertThat(revision.analysis().impacts())
        .filteredOn(i -> i.assetName().equals("SK하이닉스"))
        .singleElement()
        .satisfies(
            i -> {
              assertThat(i.direction()).isEqualTo(ImpactDirection.NEGATIVE);
              assertThat(i.expectedReaction()).isEqualTo("실적 부담이 있어요.");
            });
    assertThat(revision.analysis().overallDirection()).isEqualTo(ImpactDirection.NEUTRAL);
    assertThat(revision.analysis().overallScore()).isEqualByComparingTo("0");
    assertThat(revision.analysis().overallImpact())
        .contains("실적 부담이 있어요.")
        .doesNotContain("검증되지 않은 전체 설명");
    assertThat(revision.reason()).contains("삼성전자").doesNotContain("SK하이닉스");
    assertThat(revision.analysis().impacts())
        .extracting(PortfolioAssetImpactResponse::rank)
        .containsExactly(1, 2);
  }

  @Test
  void unconfirmedAssetKeepsWholePreviousResultWithoutPublishingInconsistentSummary() {
    var old =
        response(
            impact("삼성전자", false, ImpactDirection.NEGATIVE),
            impact("SK하이닉스", false, ImpactDirection.NEGATIVE));
    var candidate = response(impact("삼성전자", true, ImpactDirection.POSITIVE));
    assertThat(PortfolioAnalysisRefresh.compare(old, candidate).outcome())
        .isEqualTo(Outcome.UNCONFIRMED);
  }

  @Test
  void emptyEvidenceCannotConfirmUnchangedAnalysis() {
    var previous = response(insufficient("삼성전자"));
    var revision = PortfolioAnalysisRefresh.compare(previous, response(insufficient("삼성전자")));
    assertThat(revision.outcome()).isEqualTo(Outcome.UNCONFIRMED);
    assertThat(revision.analysis()).isSameAs(previous);
  }

  @Test
  void insufficientAssetDoesNotBlockNewEvidenceForAnotherAsset() {
    var previous = response(insufficient("삼성전자"), insufficient("SK하이닉스"));
    var candidate =
        response(insufficient("삼성전자"), impact("SK하이닉스", true, ImpactDirection.NEGATIVE));
    var revision = PortfolioAnalysisRefresh.compare(previous, candidate);
    assertThat(revision.outcome()).isEqualTo(Outcome.UPDATED);
    assertThat(revision.analysis().analyzedAssetCount()).isEqualTo(1);
    assertThat(revision.analysis().impacts())
        .filteredOn(i -> i.assetName().equals("삼성전자"))
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isNull());
  }

  private PortfolioAssetImpactResponse insufficient(String name) {
    return new PortfolioAssetImpactResponse(
        name,
        new BigDecimal("50"),
        null,
        null,
        "근거가 부족해요.",
        "",
        "",
        "근거가 부족해요.",
        "근거가 부족해요.",
        List.of(),
        1);
  }

  private PortfolioAnalysisResponse response(PortfolioAssetImpactResponse... impacts) {
    var list = List.of(impacts);
    return PortfolioAnalysisResponse.of(
        2,
        PortfolioImpactSummary.calculate(
            list.stream()
                .map(
                    i ->
                        new PortfolioImpactSummary.AssetImpact(
                            i.weight(), i.direction(), i.impactLevel()))
                .toList()),
        Instant.now(),
        "검증되지 않은 전체 설명이에요.",
        list,
        List.of());
  }

  private PortfolioAssetImpactResponse impact(
      String name, boolean changed, ImpactDirection direction) {
    var date = LocalDate.of(2026, changed ? 10 : 9, changed ? 1 : 25);
    String fact = changed ? "새 계약을 체결했어요." : "영업이익이 감소했어요.";
    return new PortfolioAssetImpactResponse(
        name,
        new BigDecimal("50"),
        direction,
        ImpactLevel.HIGH,
        fact,
        direction == ImpactDirection.NEGATIVE ? "실적 부담이 있어요." : "실적 개선을 기대할 수 있어요.",
        "다음 발표를 확인해 보세요.",
        fact,
        fact,
        List.of(
            new PortfolioAnalysisSourceResponse(
                "공식 발표", "https://example.invalid/" + date, date, date, fact)),
        1);
  }
}
