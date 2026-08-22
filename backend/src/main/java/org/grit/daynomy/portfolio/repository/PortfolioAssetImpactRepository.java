package org.grit.daynomy.portfolio.repository;

import org.grit.daynomy.portfolio.domain.PortfolioAssetImpact;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface PortfolioAssetImpactRepository extends JpaRepository<PortfolioAssetImpact, Long> {

    List<PortfolioAssetImpact> findAllByNewsIdAndBookmarkIdInOrderBySortOrderAsc(Long portfolioId, Collection<Long> bookmarkIds);
}
