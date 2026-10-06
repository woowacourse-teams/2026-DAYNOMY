package org.grit.daynomy.league.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.SharedHolding;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedHoldingRepository extends JpaRepository<SharedHolding, Long> {
  List<SharedHolding> findAllByPortfolioIdOrderById(Long portfolioId);

  Optional<SharedHolding> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);
}
