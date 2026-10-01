package org.grit.daynomy.asset.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StockDailyPriceRepository extends JpaRepository<StockDailyPrice, Long> {

  List<StockDailyPrice> findAllByBaseDate(LocalDate baseDate);

  Optional<StockDailyPrice> findFirstByAssetIdOrderByBaseDateDesc(Long assetId);

  List<StockDailyPrice> findTop2ByAssetIdOrderByBaseDateDesc(Long assetId);

  Optional<StockDailyPrice> findFirstByOrderByBaseDateDesc();

  Optional<StockDailyPrice> findByAssetIdAndBaseDate(Long assetId, LocalDate baseDate);

  @Query("select distinct price.baseDate from StockDailyPrice price order by price.baseDate")
  List<LocalDate> findDistinctBaseDatesOrderByBaseDate();
}
