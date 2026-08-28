package org.grit.daynomy.asset.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRankingHistoryRepository extends JpaRepository<AssetRankingHistory, Long> {

  Optional<AssetRankingHistory> findFirstByOrderByRankedDateDesc();

  Page<AssetRankingHistory> findAllByRankedDate(LocalDate rankedDate, Pageable pageable);

  @Query(
      """
      SELECT rankingHistory
      FROM AssetRankingHistory rankingHistory
      JOIN rankingHistory.asset asset
      WHERE rankingHistory.rankedDate = :rankedDate
        AND rankingHistory.ranking <= 150
        AND (
          LOWER(asset.name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
          OR LOWER(asset.assetCode) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
        )
      """)
  Page<AssetRankingHistory> searchByRankedDateAndKeyword(
      @Param("rankedDate") LocalDate rankedDate,
      @Param("keyword") String keyword,
      Pageable pageable);

  List<AssetRankingHistory> findAllByRankedDateOrderByRankingAsc(LocalDate rankedDate);

  void deleteByRankedDate(LocalDate rankedDate);
}
