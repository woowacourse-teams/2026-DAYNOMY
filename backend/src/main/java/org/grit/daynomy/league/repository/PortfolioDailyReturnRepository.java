package org.grit.daynomy.league.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PortfolioDailyReturnRepository extends JpaRepository<PortfolioDailyReturn, Long> {
  Optional<PortfolioDailyReturn> findFirstByPortfolioIdAndBaseDateOrderByCalculationVersionDesc(
      Long portfolioId, LocalDate baseDate);

  Optional<PortfolioDailyReturn> findFirstByPortfolioIdOrderByBaseDateDescCalculationVersionDesc(
      Long portfolioId);

  @Query(
      """
      select count(r) > 0 from PortfolioDailyReturn r
      where r.portfolio.id = :portfolioId and r.baseDate between :from and :to and r.eligible = true
        and exists (select d.baseDate from MarketTradingDay d where d.baseDate = r.baseDate and d.trading = true)
        and r.calculationVersion = (select max(v.calculationVersion) from PortfolioDailyReturn v
          where v.portfolio.id = r.portfolio.id and v.baseDate = r.baseDate)
      """)
  boolean existsLatestEligibleByPortfolioIdAndBaseDateBetween(
      Long portfolioId, LocalDate from, LocalDate to);

  @Query(
      """
      select r from PortfolioDailyReturn r
      where r.portfolio.id = :portfolioId and r.baseDate between :from and :to
        and exists (select d.baseDate from MarketTradingDay d where d.baseDate = r.baseDate and d.trading = true)
        and r.calculationVersion = (select max(v.calculationVersion) from PortfolioDailyReturn v
          where v.portfolio.id = r.portfolio.id and v.baseDate = r.baseDate)
      order by r.baseDate
      """)
  List<PortfolioDailyReturn> findLatestByPortfolioIdAndBaseDateBetween(
      Long portfolioId, LocalDate from, LocalDate to);
}
