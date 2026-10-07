package org.grit.daynomy.league.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.PortfolioDailyReturn;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioDailyReturnRepository extends JpaRepository<PortfolioDailyReturn, Long> {
  Optional<PortfolioDailyReturn> findByPortfolioIdAndBaseDateAndCalculationVersion(
      Long portfolioId, LocalDate baseDate, int calculationVersion);

  List<PortfolioDailyReturn> findAllByPortfolioIdAndBaseDateBetweenAndEligibleTrueOrderByBaseDate(
      Long portfolioId, LocalDate from, LocalDate to);

  List<PortfolioDailyReturn>
      findAllByPortfolioIdAndBaseDateBetweenAndCalculationVersionOrderByBaseDate(
          Long portfolioId, LocalDate from, LocalDate to, int calculationVersion);
}
