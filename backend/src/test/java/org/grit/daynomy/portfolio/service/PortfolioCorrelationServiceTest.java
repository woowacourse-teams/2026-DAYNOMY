package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioCorrelationServiceTest {

  @Mock private PortfolioHoldingRepository holdingRepository;
  @Mock private StockDailyPriceRepository priceRepository;
  @Mock private ExchangeRateService exchangeRates;
  @InjectMocks private PortfolioCorrelationService service;

  @Test
  void returnsMostOppositePairFromWeeklyReturns() {
    Asset first = asset(1L, "첫 번째");
    Asset second = asset(2L, "두 번째");
    LocalDate start = LocalDate.of(2026, 1, 2);
    double[] changes = {
      0.01, 0.02, -0.01, 0.03, -0.02, 0.015, -0.005, 0.025, -0.015, 0.01, 0.02, -0.01, 0.03, -0.02,
      0.015, -0.005, 0.025, -0.015, 0.01, 0.02, -0.01
    };
    List<StockDailyPrice> prices = new ArrayList<>();
    prices.addAll(prices(first, start, changes, false));
    prices.addAll(prices(second, start, changes, true));
    List<PortfolioHolding> holdings = List.of(holding(first), holding(second));
    given(holdingRepository.findAllByPortfolioIdOrderById(10L)).willReturn(holdings);
    given(
            priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(prices);

    var result = service.correlations(10L, LocalDate.of(2026, 6, 30));

    assertThat(result).hasSize(1);
    assertThat(result.getFirst().firstAssetName()).isEqualTo("첫 번째");
    assertThat(result.getFirst().secondAssetName()).isEqualTo("두 번째");
    assertThat(result.getFirst().correlation()).isEqualByComparingTo("-1.00");
    assertThat(result.getFirst().sampleCount()).isEqualTo(21);
  }

  @Test
  void excludesPairsWithFewerThanTwentyCommonReturns() {
    Asset first = asset(1L, "첫 번째");
    Asset second = asset(2L, "두 번째");
    double[] changes = new double[19];
    for (int index = 0; index < changes.length; index++) {
      changes[index] = index % 2 == 0 ? 0.01 : -0.01;
    }
    List<StockDailyPrice> prices = new ArrayList<>();
    LocalDate start = LocalDate.of(2026, 1, 2);
    prices.addAll(prices(first, start, changes, false));
    prices.addAll(prices(second, start, changes, true));
    List<PortfolioHolding> holdings = List.of(holding(first), holding(second));
    given(holdingRepository.findAllByPortfolioIdOrderById(10L)).willReturn(holdings);
    given(
            priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(prices);

    assertThat(service.correlations(10L, LocalDate.of(2026, 6, 30))).isEmpty();
  }

  private Asset asset(Long id, String name) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(id);
    lenient().when(asset.getDisplayName()).thenReturn(name);
    given(asset.getCurrency()).willReturn("KRW");
    return asset;
  }

  private PortfolioHolding holding(Asset asset) {
    PortfolioHolding holding = mock(PortfolioHolding.class);
    given(holding.getAsset()).willReturn(asset);
    return holding;
  }

  private List<StockDailyPrice> prices(
      Asset asset, LocalDate start, double[] changes, boolean inverse) {
    List<StockDailyPrice> prices = new ArrayList<>();
    double value = 100;
    prices.add(new StockDailyPrice(asset, start, BigDecimal.valueOf(value)));
    for (int index = 0; index < changes.length; index++) {
      value *= 1 + (inverse ? -changes[index] : changes[index]);
      prices.add(
          new StockDailyPrice(
              asset,
              start.plusWeeks(index + 1L),
              BigDecimal.valueOf(value).setScale(8, RoundingMode.HALF_UP)));
    }
    return prices;
  }
}
