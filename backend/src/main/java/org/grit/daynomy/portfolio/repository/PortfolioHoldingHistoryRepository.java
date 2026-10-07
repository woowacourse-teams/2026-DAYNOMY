package org.grit.daynomy.portfolio.repository;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioHoldingHistoryRepository
    extends JpaRepository<PortfolioHoldingHistory, Long> {

  List<PortfolioHoldingHistory>
      findAllByPortfolioIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDescIdDesc(
          Long portfolioId, Instant from, Instant to);

  List<PortfolioHoldingHistory> findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
      Long portfolioId, Instant to);
}
