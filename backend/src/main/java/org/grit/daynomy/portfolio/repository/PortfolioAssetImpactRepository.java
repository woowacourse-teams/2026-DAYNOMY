package org.grit.daynomy.portfolio.repository;

import java.util.Collection;
import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioAssetImpact;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioAssetImpactRepository extends JpaRepository<PortfolioAssetImpact, Long> {

  List<PortfolioAssetImpact> findAllByNewsIdAndBookmarkIdInOrderBySortOrderAsc(
      Long newsId, Collection<Long> bookmarkIds);
}
