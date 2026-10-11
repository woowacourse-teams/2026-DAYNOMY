package org.grit.daynomy.portfolio.repository;

import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioHoldingHistoryRepository
    extends JpaRepository<PortfolioHoldingHistory, Long> {

  List<PortfolioHoldingHistory>
      findAllByPortfolioIdAndEffectiveDateBetweenOrderByEffectiveDateDescCreatedAtDesc(
          Long portfolioId, LocalDate from, LocalDate to);

  List<PortfolioHoldingHistory>
      findAllByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAscIdAsc(
          Long portfolioId, LocalDate to);

  List<PortfolioHoldingHistory> findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(
      Long portfolioId);
}
