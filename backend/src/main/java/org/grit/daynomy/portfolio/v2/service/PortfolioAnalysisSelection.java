package org.grit.daynomy.portfolio.v2.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;

/** 새 이슈를 추측하지 않고 보유 비중과 유효한 기존 분석만으로 분석 예산을 배분한다. */
final class PortfolioAnalysisSelection {
  static final int LIMIT = 5;

  private PortfolioAnalysisSelection() {}

  static List<PortfolioAssetRequest> select(
      List<PortfolioAssetRequest> assets, Map<String, ImpactLevel> cachedLevels) {
    var ordered =
        assets.stream()
            .sorted(
                Comparator.comparing(PortfolioAssetRequest::weight)
                    .reversed()
                    .thenComparing(PortfolioAssetRequest::assetName))
            .toList();
    if (ordered.size() <= LIMIT) return ordered;
    var selected = new ArrayList<>(ordered.subList(0, 3));
    ordered.stream()
        .filter(asset -> !selected.contains(asset))
        .filter(asset -> cachedLevels.get(asset.assetName()) == ImpactLevel.HIGH)
        .limit(2)
        .forEach(selected::add);
    ordered.stream()
        .filter(asset -> !selected.contains(asset))
        .limit(LIMIT - selected.size())
        .forEach(selected::add);
    return List.copyOf(selected);
  }
}
