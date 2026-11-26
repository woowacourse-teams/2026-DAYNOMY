package org.grit.daynomy.portfolio.domain;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import org.grit.daynomy.asset.domain.AssetCategory;

public record PortfolioReviewProposalSnapshot(List<Asset> assets) {

  private static final BigDecimal TOTAL_WEIGHT = new BigDecimal("100");

  public PortfolioReviewProposalSnapshot {
    assets = List.copyOf(Objects.requireNonNull(assets));
    if (assets.isEmpty() || assets.size() > 50) {
      throw new IllegalArgumentException("검토안 자산은 1개 이상 50개 이하여야 합니다.");
    }
    var assetIds = new HashSet<Long>();
    var assetNames = new HashSet<String>();
    BigDecimal total = BigDecimal.ZERO;
    for (Asset asset : assets) {
      if (!assetIds.add(asset.assetId())
          || !assetNames.add(asset.assetName().toLowerCase(Locale.ROOT))) {
        throw new IllegalArgumentException("검토안에는 같은 자산을 중복할 수 없습니다.");
      }
      total = total.add(asset.weight());
    }
    if (total.compareTo(TOTAL_WEIGHT) != 0) {
      throw new IllegalArgumentException("검토안 자산 비중의 합은 100이어야 합니다.");
    }
  }

  public record Asset(Long assetId, String assetName, AssetCategory category, BigDecimal weight) {

    public Asset {
      if (assetId == null || assetId <= 0) {
        throw new IllegalArgumentException("검토안 자산 ID는 양수여야 합니다.");
      }
      assetName = Objects.requireNonNull(assetName).strip();
      if (assetName.isEmpty() || assetName.length() > 100) {
        throw new IllegalArgumentException("검토안 자산 이름은 1자 이상 100자 이하여야 합니다.");
      }
      if (category != AssetCategory.STOCK && category != AssetCategory.ETF) {
        throw new IllegalArgumentException("검토안에는 주식과 ETF만 추가할 수 있습니다.");
      }
      if (weight == null
          || weight.signum() <= 0
          || weight.compareTo(TOTAL_WEIGHT) > 0
          || weight.scale() > 2) {
        throw new IllegalArgumentException("검토안 자산 비중은 0 초과 100 이하의 소수 둘째 자리까지 허용합니다.");
      }
    }
  }
}
