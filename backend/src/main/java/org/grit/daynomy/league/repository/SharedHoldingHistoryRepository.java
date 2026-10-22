package org.grit.daynomy.league.repository;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.league.domain.SharedHoldingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SharedHoldingHistoryRepository extends JpaRepository<SharedHoldingHistory, Long> {
  @Query(
      """
      select h from SharedHoldingHistory h join fetch h.asset
      where h.portfolio.id = :portfolioId and coalesce(h.effectiveAt, h.createdAt) < :before
      order by coalesce(h.effectiveAt, h.createdAt), h.id
      """)
  List<SharedHoldingHistory> findEffectiveBefore(
      @Param("portfolioId") Long portfolioId, @Param("before") Instant before);
}
