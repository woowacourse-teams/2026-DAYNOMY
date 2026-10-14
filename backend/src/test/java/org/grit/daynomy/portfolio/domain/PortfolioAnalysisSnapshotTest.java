package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSnapshot.Asset;
import org.junit.jupiter.api.Test;

class PortfolioAnalysisSnapshotTest {
  @Test
  void assetOrderCaseAndDecimalScaleDoNotInvalidateAnalysis() {
    assertThat(
            PortfolioAnalysisSnapshot.sameAssets(
                List.of(
                    new Asset(" Apple ", new BigDecimal("60.00")),
                    new Asset("삼성전자", new BigDecimal("40"))),
                List.of(
                    new Asset("삼성전자", new BigDecimal("40.0")),
                    new Asset("apple", new BigDecimal("60")))))
        .isTrue();
  }

  @Test
  void changedQuantityAndLegacySnapshotWithoutQuantityInvalidateAnalysis() {
    var current = List.of(new Asset("삼성전자", new BigDecimal("100"), 2L));
    assertThat(
            PortfolioAnalysisSnapshot.sameAssets(
                List.of(new Asset("삼성전자", new BigDecimal("100"), 1L)), current))
        .isFalse();
    assertThat(
            PortfolioAnalysisSnapshot.sameAssets(
                List.of(new Asset("삼성전자", new BigDecimal("100"))), current))
        .isFalse();
  }

  @Test
  void changedWeightOrAssetInvalidatesAnalysis() {
    var saved =
        List.of(new Asset("삼성전자", new BigDecimal("60")), new Asset("SK하이닉스", new BigDecimal("40")));
    assertThat(
            PortfolioAnalysisSnapshot.sameAssets(
                saved,
                List.of(
                    new Asset("삼성전자", new BigDecimal("61")),
                    new Asset("SK하이닉스", new BigDecimal("39")))))
        .isFalse();
    assertThat(
            PortfolioAnalysisSnapshot.sameAssets(
                saved,
                List.of(
                    new Asset("삼성전자", new BigDecimal("60")),
                    new Asset("다른 종목", new BigDecimal("40")))))
        .isFalse();
  }
}
