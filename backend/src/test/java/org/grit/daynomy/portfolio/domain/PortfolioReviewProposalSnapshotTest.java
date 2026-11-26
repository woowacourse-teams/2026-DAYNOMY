package org.grit.daynomy.portfolio.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.junit.jupiter.api.Test;

class PortfolioReviewProposalSnapshotTest {

  @Test
  void acceptsUniqueStockAndEtfAssetsWhoseWeightsTotalOneHundred() {
    var snapshot =
        new PortfolioReviewProposalSnapshot(
            List.of(
                asset(1L, "삼성전자", AssetCategory.STOCK, "60"),
                asset(2L, "KODEX 200", AssetCategory.ETF, "40")));

    assertThat(snapshot.assets())
        .extracting(PortfolioReviewProposalSnapshot.Asset::assetName)
        .containsExactly("삼성전자", "KODEX 200");
  }

  @Test
  void rejectsDuplicateAssetsAndInvalidTotalWeight() {
    assertThatThrownBy(
            () ->
                new PortfolioReviewProposalSnapshot(
                    List.of(
                        asset(1L, "삼성전자", AssetCategory.STOCK, "50"),
                        asset(1L, "다른 이름", AssetCategory.STOCK, "50"))))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () ->
                new PortfolioReviewProposalSnapshot(
                    List.of(asset(1L, "삼성전자", AssetCategory.STOCK, "99"))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void rejectsUnsupportedCategoryAndWeightPrecision() {
    assertThatThrownBy(() -> asset(1L, "비트코인", AssetCategory.VIRTUAL_ASSET, "100"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> asset(1L, "삼성전자", AssetCategory.STOCK, "99.999"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private PortfolioReviewProposalSnapshot.Asset asset(
      Long id, String name, AssetCategory category, String weight) {
    return new PortfolioReviewProposalSnapshot.Asset(id, name, category, new BigDecimal(weight));
  }
}
