package org.grit.daynomy.asset.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockDailyPriceRepository extends JpaRepository<StockDailyPrice, Long> {

  List<StockDailyPrice> findAllByBaseDate(LocalDate baseDate);

  Optional<StockDailyPrice> findFirstByAssetIdOrderByBaseDateDesc(Long assetId);
}
