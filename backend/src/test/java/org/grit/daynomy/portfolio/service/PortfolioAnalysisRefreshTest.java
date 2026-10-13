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
    assertThat(revision.analysis().overallImpact()).isEmpty();
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

  @Test
  void preservesOldEvidenceWhileUpdatingOtherVerifiedAsset() {
    var old =
        response(
            impact("삼성전자", false, ImpactDirection.NEGATIVE),
            impact("SK하이닉스", false, ImpactDirection.NEGATIVE));
    var candidate =
        response(insufficient("삼성전자"), impact("SK하이닉스", true, ImpactDirection.POSITIVE));
    var revision = PortfolioAnalysisRefresh.compare(old, candidate);
    assertThat(revision.outcome()).isEqualTo(Outcome.UPDATED);
    assertThat(revision.analysis().impacts())
        .filteredOn(i -> i.assetName().equals("삼성전자"))
        .singleElement()
        .satisfies(i -> assertThat(i.sources()).isEqualTo(old.impacts().getFirst().sources()));
    assertThat(revision.analysis().overallScore()).isEqualByComparingTo("0");
    assertThat(revision.analysis().analyzedAssetCount()).isEqualTo(2);
    assertThat(revision.analysis().overallImpact()).doesNotContain("검증되지 않은 전체 설명");
  }

  @Test
  void repairsStoredEnglishEvenWhenTheUnderlyingFactIsUnchanged() {
    var current = impact("포스코퓨처엠", false, ImpactDirection.POSITIVE);
    var broken =
        new PortfolioAssetImpactResponse(
            current.assetName(),
            current.weight(),
            current.direction(),
            current.impactLevel(),
            "POSCO Future M signs a supply contract..요.",
            current.expectedReaction(),
            current.outlook(),
            current.reason(),
            current.evidenceSentence(),
            current.sources(),
            1);
    var revision = PortfolioAnalysisRefresh.compare(response(broken), response(current));
    assertThat(revision.outcome()).isEqualTo(Outcome.UPDATED);
    assertThat(revision.analysis().impacts())
        .singleElement()
        .satisfies(i -> assertThat(i.issueSummary()).isEqualTo(current.issueSummary()));
    assertThat(revision.reason()).isEqualTo("포스코퓨처엠의 근거와 설명을 갱신했어요.");
  }

  @Test
  void doesNotRetainUnusableStoredHomepageAsVerifiedEvidence() {
    var good = impact("포스코퓨처엠", false, ImpactDirection.POSITIVE);
    var bad =
        new PortfolioAssetImpactResponse(
            good.assetName(),
            good.weight(),
            good.direction(),
            good.impactLevel(),
            good.issueSummary(),
            good.expectedReaction(),
            good.outlook(),
            good.reason(),
            good.evidenceSentence(),
            List.of(new PortfolioAnalysisSourceResponse("뉴스룸", "https://newsroom.posco.com/en/")),
            1);
    var revision =
        PortfolioAnalysisRefresh.compare(response(bad), response(insufficient("포스코퓨처엠")));
    assertThat(revision.outcome()).isEqualTo(Outcome.UPDATED);
    assertThat(revision.analysis().impacts())
        .singleElement()
        .satisfies(i -> assertThat(i.direction()).isNull());
  }

  @Test
  void preservesAllFourAssetsForSummaryInsteadOfSelectingTopThree() {
    var previous =
        response(insufficient("가"), insufficient("나"), insufficient("다"), insufficient("라"));
    var candidate =
        response(
            impact("가", true, ImpactDirection.POSITIVE),
            impact("나", true, ImpactDirection.NEGATIVE),
            impact("다", true, ImpactDirection.POSITIVE),
            impact("라", true, ImpactDirection.POSITIVE));
    assertThat(PortfolioAnalysisRefresh.compare(previous, candidate).analysis().impacts())
        .hasSize(4);
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
