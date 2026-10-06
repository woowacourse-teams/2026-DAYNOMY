package org.grit.daynomy.league.repository;

import java.util.Optional;
import org.grit.daynomy.league.domain.SharedPortfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedPortfolioRepository extends JpaRepository<SharedPortfolio, Long> {
  Optional<SharedPortfolio> findByMemberId(Long memberId);
}
