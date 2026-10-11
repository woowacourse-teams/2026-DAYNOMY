package org.grit.daynomy.asset.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketTradingDayRepository extends JpaRepository<MarketTradingDay, LocalDate> {
  List<MarketTradingDay> findAllByBaseDateBetweenOrderByBaseDate(LocalDate from, LocalDate to);

  Optional<MarketTradingDay> findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(
      LocalDate date);

  List<MarketTradingDay> findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
      LocalDate date);
}
