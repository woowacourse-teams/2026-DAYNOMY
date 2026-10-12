package org.grit.daynomy.league.repository;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SharedHoldingHistoryRepository extends JpaRepository<SharedHoldingHistory, Long> {
  List<SharedHoldingHistory> findAllByPortfolioIdAndCreatedAtLessThanOrderByCreatedAtAscIdAsc(
      Long portfolioId, Instant before);
}
