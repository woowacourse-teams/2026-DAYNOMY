package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PortfolioCalculationServiceTest {

  @Mock private StockDailyPriceRepository stockDailyPriceRepository;
  @InjectMocks private PortfolioCalculationService calculationService;

  @Test
  @DisplayName("최근 종가로 포트폴리오 손익과 종목·시장 비중을 계산한다")
  void calculatePortfolio() {
    Asset samsung = stock(1L, "005930", "삼성전자", StockMarket.KOSPI);
    Asset ecoPro = stock(2L, "247540", "에코프로비엠", StockMarket.KOSDAQ);
    given(stockDailyPriceRepository.findFirstByAssetIdOrderByBaseDateDesc(1L))
        .willReturn(
            Optional.of(
                new StockDailyPrice(samsung, LocalDate.of(2026, 9, 18), new BigDecimal("82000"))));
    given(stockDailyPriceRepository.findFirstByAssetIdOrderByBaseDateDesc(2L))
        .willReturn(
            Optional.of(
                new StockDailyPrice(ecoPro, LocalDate.of(2026, 9, 17), new BigDecimal("285000"))));
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(
                new PortfolioHoldingRequest(1L, 10L, new BigDecimal("65000")),
                new PortfolioHoldingRequest(2L, 2L, new BigDecimal("300000"))));

    var response = calculationService.calculate(request);

    assertThat(response.baseDate()).isEqualTo(LocalDate.of(2026, 9, 17));
    assertThat(response.totalPurchaseAmount()).isEqualByComparingTo("1250000.00");
    assertThat(response.totalEvaluationAmount()).isEqualByComparingTo("1390000.00");
    assertThat(response.totalProfitLoss()).isEqualByComparingTo("140000.00");
    assertThat(response.totalReturnRate()).isEqualByComparingTo("11.20");
    assertThat(response.holdings())
        .extracting(holding -> holding.assetCode(), holding -> holding.weight())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("005930", new BigDecimal("58.99")),
            org.assertj.core.groups.Tuple.tuple("247540", new BigDecimal("41.01")));
    assertThat(response.marketAllocations())
        .extracting(allocation -> allocation.market(), allocation -> allocation.weight())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(StockMarket.KOSPI, new BigDecimal("58.99")),
            org.assertj.core.groups.Tuple.tuple(StockMarket.KOSDAQ, new BigDecimal("41.01")));
  }

  @Test
  @DisplayName("같은 종목이 중복되면 포트폴리오 계산을 거부한다")
  void rejectDuplicateAssets() {
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(
                new PortfolioHoldingRequest(1L, 10L, new BigDecimal("65000")),
                new PortfolioHoldingRequest(1L, 5L, new BigDecimal("70000"))));

    assertThatThrownBy(() -> calculationService.calculate(request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.DUPLICATE_PORTFOLIO_ASSET);
    verifyNoInteractions(stockDailyPriceRepository);
  }

  @Test
  @DisplayName("종가가 없는 종목이 포함되면 포트폴리오 계산을 중단한다")
  void rejectAssetWithoutPrice() {
    given(stockDailyPriceRepository.findFirstByAssetIdOrderByBaseDateDesc(1L))
        .willReturn(Optional.empty());
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 10L, new BigDecimal("65000"))));

    assertThatThrownBy(() -> calculationService.calculate(request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_NOT_FOUND);
  }

  private Asset stock(Long id, String code, String name, StockMarket market) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(id);
    given(asset.getAssetCode()).willReturn(code);
    given(asset.getName()).willReturn(name);
    given(asset.getMarket()).willReturn(market);
    return asset;
  }
}
