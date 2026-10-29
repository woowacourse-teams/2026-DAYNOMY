package org.grit.daynomy.asset.service;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;

import java.time.LocalDate;
import java.util.Map;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.league.service.LeagueReturnScheduler;
import org.grit.daynomy.league.service.LeagueReturnService;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.PortfolioCalculationService;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotService;
import org.grit.daynomy.portfolio.service.PortfolioSnapshotTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.scheduling.TaskScheduler;

class StockPriceFollowUpOrderTest {
  @Test
  void refreshesSnapshotsBeforeLeagueCalculationAfterPriceCommit() {
    var portfolios = mock(PortfolioRepository.class);
    var returns = mock(LeagueReturnService.class);
    var snapshots =
        new PortfolioSnapshotService(
            portfolios,
            mock(PortfolioHoldingHistoryRepository.class),
            mock(PortfolioHoldingRepository.class),
            mock(PortfolioDailySnapshotRepository.class),
            mock(StockDailyPriceRepository.class),
            mock(PortfolioSnapshotTransactionService.class),
            mock(PortfolioCalculationService.class));
    var league = new LeagueReturnScheduler(returns, mock(TaskScheduler.class));
    try (var context = new AnnotationConfigApplicationContext()) {
      context
          .getEnvironment()
          .getPropertySources()
          .addFirst(new MapPropertySource("test", Map.of("league.calculation.enabled", "true")));
      context.registerBean(PortfolioSnapshotService.class, () -> snapshots);
      context.registerBean(LeagueReturnScheduler.class, () -> league);
      context.refresh();
      var result = new StockPriceSyncResult(LocalDate.of(2026, 10, 27), 1, 1, 0, 0);

      context.publishEvent(result);

      var order = inOrder(portfolios, returns);
      order.verify(portfolios).findAll();
      order.verify(returns).recalculateFrom(result.earliestBaseDate());
    }
  }
}
