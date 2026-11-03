package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.dto.PortfolioPricingStatus;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class PortfolioCalculationServiceTest {

  @Mock private StockDailyPriceRepository stockDailyPriceRepository;
  @Mock private AssetRepository assetRepository;
  @Mock private org.grit.daynomy.exchangerate.service.ExchangeRateService exchangeRates;
  @Mock private org.springframework.transaction.PlatformTransactionManager transactionManager;
  @InjectMocks private PortfolioCalculationService calculationService;

  private final Logger logger = (Logger) LoggerFactory.getLogger(PortfolioCalculationService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private Level originalLevel;

  @BeforeEach
  void setUpLogging() {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownLogging() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
  }

  @Test
  @DisplayName("주식과 ETF의 최근 종가로 혼합 포트폴리오 손익과 비중을 계산한다")
  void calculatePortfolio() {
    Asset samsung = asset(1L, "005930", "삼성전자", AssetCategory.STOCK, StockMarket.KOSPI);
    Asset kodex200 = asset(2L, "069500", "KODEX 200", AssetCategory.ETF, StockMarket.KOSPI);
    given(stockDailyPriceRepository.findLatestTwoByAssetIds(List.of(1L, 2L)))
        .willReturn(
            List.of(
                new StockDailyPrice(samsung, LocalDate.of(2026, 9, 18), new BigDecimal("82000")),
                new StockDailyPrice(
                    kodex200, LocalDate.of(2026, 9, 17), new BigDecimal("285000"))));
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(
                new PortfolioHoldingRequest(1L, 10L, new BigDecimal("65000")),
                new PortfolioHoldingRequest(2L, 2L, new BigDecimal("300000"))));

    var response = calculationService.calculate(request);

    verify(stockDailyPriceRepository).findLatestTwoByAssetIds(List.of(1L, 2L));
    verifyNoMoreInteractions(stockDailyPriceRepository);

    assertThat(response.baseDate()).isEqualTo(LocalDate.of(2026, 9, 17));
    assertThat(response.pricingStatus()).isEqualTo(PortfolioPricingStatus.MIXED_DATES);
    assertThat(response.totalPurchaseAmount()).isEqualByComparingTo("1250000.00");
    assertThat(response.totalEvaluationAmount()).isEqualByComparingTo("1390000.00");
    assertThat(response.totalProfitLoss()).isEqualByComparingTo("140000.00");
    assertThat(response.totalReturnRate()).isEqualByComparingTo("11.20");
    assertThat(response.dailyProfitLoss()).isNull();
    assertThat(response.dailyReturnRate()).isNull();
    assertThat(response.holdings())
        .extracting(
            holding -> holding.assetCode(),
            holding -> holding.category(),
            holding -> holding.weight())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "005930", AssetCategory.STOCK, new BigDecimal("58.99")),
            org.assertj.core.groups.Tuple.tuple(
                "069500", AssetCategory.ETF, new BigDecimal("41.01")));
    assertThat(response.marketAllocations())
        .extracting(allocation -> allocation.market(), allocation -> allocation.weight())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(StockMarket.KOSPI, new BigDecimal("100.00")));

    assertThat(appender.list).hasSize(2);
    ILoggingEvent completedLog = appender.list.get(1);
    assertThat(completedLog.getLevel()).isEqualTo(Level.INFO);
    assertThat(completedLog.getFormattedMessage())
        .isEqualTo(LogEvent.PORTFOLIO_CALCULATION_COMPLETED.message());
    assertThat(keyValues(completedLog))
        .containsEntry("event", LogEvent.PORTFOLIO_CALCULATION_COMPLETED.code())
        .containsEntry("holdingCount", 2)
        .containsEntry("marketCount", 1)
        .containsEntry("baseDate", LocalDate.of(2026, 9, 17))
        .containsKey("durationMs")
        .doesNotContainKeys(
            "assetId",
            "quantity",
            "averagePurchasePrice",
            "totalPurchaseAmount",
            "totalEvaluationAmount",
            "totalProfitLoss");
  }

  @Test
  @DisplayName("모든 종목의 최근 거래일이 같으면 오늘 손익과 수익률을 계산한다")
  void calculateDailyPerformance() {
    Asset samsung = asset(1L, "005930", "삼성전자", AssetCategory.STOCK, StockMarket.KOSPI);
    given(stockDailyPriceRepository.findLatestTwoByAssetIds(List.of(1L)))
        .willReturn(
            List.of(
                new StockDailyPrice(samsung, LocalDate.of(2026, 9, 18), new BigDecimal("82000")),
                new StockDailyPrice(samsung, LocalDate.of(2026, 9, 17), new BigDecimal("80000"))));
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 10L, new BigDecimal("65000"))));

    var response = calculationService.calculate(request);

    assertThat(response.dailyProfitLoss()).isEqualByComparingTo("20000.00");
    assertThat(response.dailyReturnRate()).isEqualByComparingTo("2.50");
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
  @DisplayName("종가가 없는 종목은 가격 미확인 상태로 남기고 나머지 계산을 제공한다")
  void keepAssetWithoutPriceAsUnpriced() {
    Asset samsung = asset(1L, "005930", "삼성전자", AssetCategory.STOCK, StockMarket.KOSPI);
    given(stockDailyPriceRepository.findLatestTwoByAssetIds(List.of(1L))).willReturn(List.of());
    given(assetRepository.findById(1L)).willReturn(java.util.Optional.of(samsung));
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 10L, new BigDecimal("65000"))));

    var response = calculationService.calculate(request);

    assertThat(response.pricingStatus()).isEqualTo(PortfolioPricingStatus.PARTIAL);
    assertThat(response.pricedHoldingCount()).isZero();
    assertThat(response.unpricedHoldingCount()).isEqualTo(1);
    assertThat(response.totalPurchaseAmount()).isEqualByComparingTo("650000.00");
    assertThat(response.totalProfitLoss()).isNull();
    assertThat(response.totalReturnRate()).isNull();
    assertThat(response.holdings().getFirst().priceAvailable()).isFalse();
    assertThat(response.holdings().getFirst().closePrice()).isNull();
  }

  @Test
  @DisplayName("일괄 조회 결과 순서와 무관하게 보유 순서대로 비중과 하루 손익을 계산한다")
  void calculatesMultipleAssetsInHoldingOrder() {
    Asset first = asset(1L, "FIRST", "첫 종목", AssetCategory.STOCK, StockMarket.KOSPI);
    Asset second = asset(2L, "SECOND", "둘째 종목", AssetCategory.ETF, StockMarket.KOSPI);
    given(stockDailyPriceRepository.findLatestTwoByAssetIds(List.of(2L, 1L)))
        .willReturn(
            List.of(
                new StockDailyPrice(first, LocalDate.of(2026, 9, 18), new BigDecimal("120")),
                new StockDailyPrice(first, LocalDate.of(2026, 9, 17), new BigDecimal("100")),
                new StockDailyPrice(second, LocalDate.of(2026, 9, 18), new BigDecimal("80")),
                new StockDailyPrice(second, LocalDate.of(2026, 9, 17), new BigDecimal("100"))));

    var response =
        calculationService.calculate(
            new PortfolioCalculateRequest(
                List.of(
                    new PortfolioHoldingRequest(2L, 1L, new BigDecimal("100")),
                    new PortfolioHoldingRequest(1L, 2L, new BigDecimal("100")))));

    assertThat(response.totalEvaluationAmount()).isEqualByComparingTo("320.00");
    assertThat(response.totalPurchaseAmount()).isEqualByComparingTo("300.00");
    assertThat(response.dailyProfitLoss()).isEqualByComparingTo("20.00");
    assertThat(response.dailyReturnRate()).isEqualByComparingTo("6.67");
    assertThat(response.holdings())
        .extracting(holding -> holding.assetCode(), holding -> holding.weight())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("SECOND", new BigDecimal("25.00")),
            org.assertj.core.groups.Tuple.tuple("FIRST", new BigDecimal("75.00")));
    verify(stockDailyPriceRepository).findLatestTwoByAssetIds(List.of(2L, 1L));
    verifyNoMoreInteractions(stockDailyPriceRepository);
  }

  @Test
  @DisplayName("일부 종목의 가격이 누락되어도 보유 종목을 유지하고 전체 손익 판단을 보류한다")
  void retainsMissingAssetInMixedPortfolio() {
    Asset priced = asset(1L, "PRICED", "가격 있음", AssetCategory.STOCK, StockMarket.KOSPI);
    Asset missing = asset(2L, "MISSING", "가격 없음", AssetCategory.STOCK, StockMarket.KOSPI);
    given(stockDailyPriceRepository.findLatestTwoByAssetIds(List.of(1L, 2L)))
        .willReturn(
            List.of(new StockDailyPrice(priced, LocalDate.of(2026, 9, 18), new BigDecimal("120"))));
    given(assetRepository.findById(2L)).willReturn(java.util.Optional.of(missing));

    var response =
        calculationService.calculate(
            new PortfolioCalculateRequest(
                List.of(
                    new PortfolioHoldingRequest(1L, 2L, new BigDecimal("100")),
                    new PortfolioHoldingRequest(2L, 1L, new BigDecimal("100")))));

    assertThat(response.pricingStatus()).isEqualTo(PortfolioPricingStatus.PARTIAL);
    assertThat(response.pricedHoldingCount()).isEqualTo(1);
    assertThat(response.unpricedHoldingCount()).isEqualTo(1);
    assertThat(response.totalPurchaseAmount()).isEqualByComparingTo("300.00");
    assertThat(response.totalEvaluationAmount()).isEqualByComparingTo("240.00");
    assertThat(response.totalProfitLoss()).isNull();
    assertThat(response.dailyProfitLoss()).isNull();
    assertThat(response.holdings())
        .extracting(holding -> holding.assetCode(), holding -> holding.priceAvailable())
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("PRICED", true),
            org.assertj.core.groups.Tuple.tuple("MISSING", false));
  }

  private Asset asset(
      Long id, String code, String name, AssetCategory category, StockMarket market) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(id);
    given(asset.getAssetCode()).willReturn(code);
    given(asset.getDisplayName()).willReturn(name);
    given(asset.getCategory()).willReturn(category);
    given(asset.getMarket()).willReturn(market);
    return asset;
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }

  @Test
  void usdPricesAreConvertedBeforeKrwPortfolioCalculation() {
    Asset apple =
        Asset.listedStock("Apple", "AAPL.US", StockMarket.NASDAQ, null, LocalDate.of(2026, 10, 16));
    apple.synchronizeKoreanName("애플");
    org.springframework.test.util.ReflectionTestUtils.setField(apple, "id", 1L);
    given(stockDailyPriceRepository.findLatestTwoByAssetIds(List.of(1L)))
        .willReturn(
            List.of(
                new StockDailyPrice(
                    apple, LocalDate.of(2026, 10, 16), new BigDecimal("245.1234"))));
    var request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 1L, new BigDecimal("200"))));
    given(exchangeRates.ratesForDates(List.of(LocalDate.of(2026, 10, 16))))
        .willReturn(Map.of(LocalDate.of(2026, 10, 16), new BigDecimal("1300")));
    var result = calculationService.calculate(request);
    assertThat(result.totalEvaluationAmount()).isEqualByComparingTo("318660.42");
    assertThat(result.holdings().getFirst().name()).isEqualTo("애플");
    assertThat(result.holdings().getFirst().closePrice()).isEqualByComparingTo("245.1234");
  }

  @Test
  void usdAssetWithoutPriceRemainsUnpriced() {
    Asset apple =
        Asset.listedStock("Apple", "AAPL.US", StockMarket.NASDAQ, null, LocalDate.of(2026, 10, 16));
    org.springframework.test.util.ReflectionTestUtils.setField(apple, "id", 1L);
    given(stockDailyPriceRepository.findLatestTwoByAssetIds(List.of(1L))).willReturn(List.of());
    given(assetRepository.findById(1L)).willReturn(java.util.Optional.of(apple));
    var request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 1L, new BigDecimal("200"))));
    var result = calculationService.calculate(request);
    assertThat(result.unpricedHoldingCount()).isEqualTo(1);
    assertThat(result.holdings().getFirst().name()).isEqualTo("Apple");
    assertThat(result.totalProfitLoss()).isNull();
  }
}
