package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.v1.service.PortfolioCalculationServiceV1;
import org.grit.daynomy.portfolio.v2.service.PortfolioCalculationServiceV2;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class VersionedPortfolioCalculationServiceTest {
  @Mock private StockDailyPriceRepository prices;
  @Mock private AssetRepository assets;
  @Mock private org.grit.daynomy.exchangerate.service.ExchangeRateService exchangeRates;

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void versionsProduceTheSameCalculationWithCompleteOrMissingPrices(boolean missingPrice) {
    var samsung = asset(1L, "삼성전자", "005930", "KR7005930003");
    var hynix = asset(2L, "SK하이닉스", "000660", "KR7000660001");
    var samsungPrices =
        List.of(
            new StockDailyPrice(samsung, LocalDate.of(2026, 10, 13), new BigDecimal("120")),
            new StockDailyPrice(samsung, LocalDate.of(2026, 10, 12), new BigDecimal("100")));
    var hynixPrices =
        missingPrice
            ? List.<StockDailyPrice>of()
            : List.of(
                new StockDailyPrice(hynix, LocalDate.of(2026, 10, 13), new BigDecimal("210")),
                new StockDailyPrice(hynix, LocalDate.of(2026, 10, 12), new BigDecimal("200")));
    given(prices.findTop2ByAssetIdOrderByBaseDateDesc(1L)).willReturn(samsungPrices);
    given(prices.findTop2ByAssetIdOrderByBaseDateDesc(2L)).willReturn(hynixPrices);
    var combined = new java.util.ArrayList<>(samsungPrices);
    combined.addAll(hynixPrices);
    given(prices.findLatestTwoByAssetIds(List.of(1L, 2L))).willReturn(combined);
    if (missingPrice) given(assets.findById(2L)).willReturn(Optional.of(hynix));
    var request =
        new PortfolioCalculateRequest(
            List.of(
                new PortfolioHoldingRequest(1L, 10L, new BigDecimal("90")),
                new PortfolioHoldingRequest(2L, 2L, new BigDecimal("180"))));

    var before =
        new PortfolioCalculationServiceV1(prices, assets, exchangeRates).calculate(request);
    var after = new PortfolioCalculationServiceV2(prices, assets, exchangeRates).calculate(request);

    assertThat(after).isEqualTo(before);
    verify(prices).findTop2ByAssetIdOrderByBaseDateDesc(1L);
    verify(prices).findTop2ByAssetIdOrderByBaseDateDesc(2L);
    verify(prices).findLatestTwoByAssetIds(List.of(1L, 2L));
  }

  private Asset asset(Long id, String name, String code, String isin) {
    var asset = Asset.listedStock(name, code, StockMarket.KOSPI, isin, LocalDate.of(2026, 1, 1));
    ReflectionTestUtils.setField(asset, "id", id);
    return asset;
  }
}
