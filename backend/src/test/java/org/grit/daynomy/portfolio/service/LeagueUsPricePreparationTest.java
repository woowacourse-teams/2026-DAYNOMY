package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.grit.daynomy.external.eodhd.dto.EodhdDailyPrice;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class LeagueUsPricePreparationTest {
  @Mock private AssetRepository assets;
  @Mock private TaskScheduler scheduler;
  @Mock private StockDailyPriceRepository storedPrices;
  @Mock private MarketTradingDayRepository days;
  @Mock private EodhdClient usClient;
  @Mock private PlatformTransactionManager transactions;
  @InjectMocks private PortfolioPriceHistoryService prices;

  @Test
  void collectsLeagueUsAssetEvenWithoutAnOriginalPortfolio() {
    LocalDate from = LocalDate.now().minusWeeks(2).with(DayOfWeek.MONDAY);
    Asset asset =
        Asset.listedSecurity("Apple", AssetCategory.STOCK, "AAPL", StockMarket.NASDAQ, null, from);
    ReflectionTestUtils.setField(asset, "id", 7L);
    given(assets.findAllById(List.of(7L))).willReturn(List.of(asset));
    given(assets.findById(7L)).willReturn(Optional.of(asset));
    given(usClient.getUsMarketHolidays(from, from)).willReturn(Set.of());
    given(usClient.getHistoricalPrices("AAPL", from, from))
        .willReturn(List.of(new EodhdDailyPrice("AAPL", "US", from, new BigDecimal("100"))));
    given(transactions.getTransaction(any(TransactionDefinition.class)))
        .willReturn(new SimpleTransactionStatus());

    prices.prepareLeagueUsPrices(List.of(7L), from, from);

    ArgumentCaptor<Runnable> job = ArgumentCaptor.forClass(Runnable.class);
    verify(scheduler).schedule(job.capture(), any(Instant.class));
    job.getValue().run();
    verify(storedPrices).saveAll(any());
    verify(days).markUsCollectedDates(eq(List.of(from)), any(Instant.class));
    assertThat(asset.hasPriceHistory(from, from)).isTrue();
  }
}
