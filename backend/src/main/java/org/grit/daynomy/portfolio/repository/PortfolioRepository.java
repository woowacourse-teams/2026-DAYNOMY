package org.grit.daynomy.portfolio.repository;

import java.util.Optional;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

  Optional<Portfolio> findByMemberId(Long memberId);
}
