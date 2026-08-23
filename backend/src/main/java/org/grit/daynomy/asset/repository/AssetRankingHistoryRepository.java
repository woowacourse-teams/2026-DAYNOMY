package org.grit.daynomy.asset.repository;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRankingHistoryRepository extends JpaRepository<AssetRankingHistory, Long> {

  List<AssetRankingHistory> findAllByRankedDateOrderByRankingAsc(LocalDate rankedDate);

  void deleteByRankedDate(LocalDate rankedDate);
}
