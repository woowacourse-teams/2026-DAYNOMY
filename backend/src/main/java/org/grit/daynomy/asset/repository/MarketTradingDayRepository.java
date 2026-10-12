package org.grit.daynomy.asset.repository;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface MarketTradingDayRepository extends JpaRepository<MarketTradingDay, LocalDate> {
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select d from MarketTradingDay d where d.baseDate = :date")
  Optional<MarketTradingDay> findByIdForUpdate(LocalDate date);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select d from MarketTradingDay d where d.baseDate between :from and :to order by d.baseDate")
  List<MarketTradingDay> findCalendarForUpdate(LocalDate from, LocalDate to);

  List<MarketTradingDay> findAllByBaseDateBetweenOrderByBaseDate(LocalDate from, LocalDate to);

  Optional<MarketTradingDay> findFirstByTradingTrueAndBaseDateLessThanOrderByBaseDateDesc(
      LocalDate date);

  Optional<MarketTradingDay>
      findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
          LocalDate date);

  List<MarketTradingDay> findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(
      LocalDate date);
}
