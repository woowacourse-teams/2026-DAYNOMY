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
import org.grit.daynomy.portfolio.dto.PortfolioCorrelationResponse;
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

  @Test
  void ranksByRawCorrelationBeforeRoundingAndLimiting() {
    Asset base = asset(1L, "기준");
    List<Asset> candidates =
        List.of(asset(2L, "약한"), asset(3L, "둘째"), asset(4L, "셋째"), asset(5L, "강한"));
    double[] targets = {-0.721, -0.722, -0.723, -0.724};
    double[] baseChanges = normalizedWave(21, 1);
    List<double[]> noises = new ArrayList<>();
    List<StockDailyPrice> prices = new ArrayList<>();
    LocalDate start = LocalDate.of(2026, 1, 2);
    prices.addAll(prices(base, start, scaled(baseChanges), false));
    for (int index = 0; index < candidates.size(); index++) {
      double[] noise = orthogonalWave(21, index + 2, baseChanges, noises);
      noises.add(noise);
      prices.addAll(
          prices(
              candidates.get(index), start, correlated(baseChanges, noise, targets[index]), false));
    }
    List<PortfolioHolding> holdings = new ArrayList<>();
    holdings.add(holding(base));
    candidates.forEach(asset -> holdings.add(holding(asset)));
    given(holdingRepository.findAllByPortfolioIdOrderById(10L)).willReturn(holdings);
    given(
            priceRepository.findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(prices);

    var result = service.correlations(10L, LocalDate.of(2026, 6, 30));

    assertThat(result)
        .extracting(PortfolioCorrelationResponse::secondAssetName)
        .containsExactly("강한", "셋째", "둘째");
    assertThat(result)
        .extracting(PortfolioCorrelationResponse::correlation)
        .allSatisfy(value -> assertThat(value).isEqualByComparingTo("-0.72"));
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

  private double[] correlated(double[] base, double[] noise, double target) {
    double[] values = new double[base.length];
    double noiseWeight = Math.sqrt(1 - target * target);
    for (int index = 0; index < values.length; index++) {
      values[index] = 0.05 * (target * base[index] + noiseWeight * noise[index]);
    }
    return values;
  }

  private double[] scaled(double[] values) {
    double[] result = values.clone();
    for (int index = 0; index < result.length; index++) result[index] *= 0.05;
    return result;
  }

  private double[] normalizedWave(int length, int frequency) {
    double[] values = new double[length];
    for (int index = 0; index < length; index++) {
      values[index] = Math.sin((index + 1) * frequency);
    }
    normalize(values, List.of());
    return values;
  }

  private double[] orthogonalWave(
      int length, int frequency, double[] base, List<double[]> previous) {
    double[] values = new double[length];
    for (int index = 0; index < length; index++) {
      values[index] = Math.cos((index + 1) * frequency);
    }
    List<double[]> references = new ArrayList<>();
    references.add(base);
    references.addAll(previous);
    normalize(values, references);
    return values;
  }

  private void normalize(double[] values, List<double[]> references) {
    double mean = 0;
    for (double value : values) mean += value;
    mean /= values.length;
    for (int index = 0; index < values.length; index++) values[index] -= mean;
    for (double[] reference : references) {
      double projection = 0;
      for (int index = 0; index < values.length; index++) {
        projection += values[index] * reference[index];
      }
      for (int index = 0; index < values.length; index++) {
        values[index] -= projection * reference[index];
      }
    }
    double magnitude = 0;
    for (double value : values) magnitude += value * value;
    magnitude = Math.sqrt(magnitude);
    for (int index = 0; index < values.length; index++) values[index] /= magnitude;
  }
}
