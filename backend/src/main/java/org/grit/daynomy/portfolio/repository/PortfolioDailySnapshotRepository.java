package org.grit.daynomy.portfolio.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioDailySnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioDailySnapshotRepository
    extends JpaRepository<PortfolioDailySnapshot, Long> {

  Optional<PortfolioDailySnapshot> findByPortfolioIdAndBaseDate(
      Long portfolioId, LocalDate baseDate);

  List<PortfolioDailySnapshot> findAllByPortfolioIdAndBaseDateBetweenOrderByBaseDate(
      Long portfolioId, LocalDate from, LocalDate to);
}
