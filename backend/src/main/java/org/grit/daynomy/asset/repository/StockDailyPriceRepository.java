package org.grit.daynomy.asset.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockDailyPriceRepository extends JpaRepository<StockDailyPrice, Long> {

  List<StockDailyPrice> findAllByBaseDate(LocalDate baseDate);

  List<StockDailyPrice> findAllByAssetIdInAndBaseDate(List<Long> assetIds, LocalDate baseDate);

  Optional<StockDailyPrice> findFirstByAssetIdOrderByBaseDateDesc(Long assetId);

  List<StockDailyPrice> findTop2ByAssetIdOrderByBaseDateDesc(Long assetId);

  @Query(
      value =
          """
          SELECT price.*
          FROM assets asset
          CROSS JOIN LATERAL (
              SELECT daily_price.*
              FROM stock_daily_prices daily_price
              WHERE daily_price.asset_id = asset.id
              ORDER BY daily_price.base_date DESC
              LIMIT 2
          ) price
          WHERE asset.id IN (:assetIds)
          ORDER BY price.asset_id, price.base_date DESC
          """,
      nativeQuery = true)
  List<StockDailyPrice> findLatestTwoByAssetIds(List<Long> assetIds);

  List<StockDailyPrice> findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
      List<Long> assetIds, LocalDate from, LocalDate to);

  Optional<StockDailyPrice> findFirstByOrderByBaseDateDesc();

  Optional<StockDailyPrice> findByAssetIdAndBaseDate(Long assetId, LocalDate baseDate);

  Optional<StockDailyPrice> findFirstByAssetIdAndBaseDateLessThanEqualOrderByBaseDateDesc(
      Long assetId, LocalDate baseDate);

  @Query("select distinct price.baseDate from StockDailyPrice price order by price.baseDate")
  List<LocalDate> findDistinctBaseDatesOrderByBaseDate();

  @Query(
      """
      select distinct p.baseDate from StockDailyPrice p
      where p.asset.id in (select h.asset.id from PortfolioHoldingHistory h where h.portfolio.id = :portfolioId)
         or p.asset.id in (select h.asset.id from PortfolioHolding h where h.portfolio.id = :portfolioId)
      order by p.baseDate
      """)
  List<LocalDate> findBaseDatesByPortfolioId(Long portfolioId);

  @Query(
      """
      select distinct p.baseDate from StockDailyPrice p
      where p.asset.id in :assetIds and p.asset.market in :markets
      order by p.baseDate
      """)
  List<LocalDate> findBaseDatesByAssetIdsAndMarkets(List<Long> assetIds, Set<StockMarket> markets);

  @Query(
      """
      select distinct p.baseDate from StockDailyPrice p
      where p.asset.id in :assetIds order by p.baseDate
      """)
  List<LocalDate> findBaseDatesByAssetIds(List<Long> assetIds);
}
