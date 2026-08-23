package org.grit.daynomy.asset.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssetRankingHistoryRepository extends JpaRepository<AssetRankingHistory, Long> {

  Optional<AssetRankingHistory> findFirstByOrderByRankedDateDesc();

  List<AssetRankingHistory> findAllByRankedDateOrderByRankingAsc(LocalDate rankedDate);

  void deleteByRankedDate(LocalDate rankedDate);
}
