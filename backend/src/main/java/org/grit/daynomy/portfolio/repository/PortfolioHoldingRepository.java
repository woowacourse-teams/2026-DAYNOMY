package org.grit.daynomy.portfolio.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {

  List<PortfolioHolding> findAllByPortfolioIdOrderById(Long portfolioId);

  Optional<PortfolioHolding> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);

  boolean existsByPortfolioIdAndAssetId(Long portfolioId, Long assetId);
}
