package org.grit.daynomy.portfolio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
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
    given(stockDailyPriceRepository.findTop2ByAssetIdOrderByBaseDateDesc(1L))
        .willReturn(
            List.of(
                new StockDailyPrice(samsung, LocalDate.of(2026, 9, 18), new BigDecimal("82000"))));
    given(stockDailyPriceRepository.findTop2ByAssetIdOrderByBaseDateDesc(2L))
        .willReturn(
            List.of(
                new StockDailyPrice(
                    kodex200, LocalDate.of(2026, 9, 17), new BigDecimal("285000"))));
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
    given(stockDailyPriceRepository.findTop2ByAssetIdOrderByBaseDateDesc(1L))
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
  @DisplayName("종가가 없는 종목이 포함되면 포트폴리오 계산을 중단한다")
  void rejectAssetWithoutPrice() {
    given(stockDailyPriceRepository.findTop2ByAssetIdOrderByBaseDateDesc(1L)).willReturn(List.of());
    PortfolioCalculateRequest request =
        new PortfolioCalculateRequest(
            List.of(new PortfolioHoldingRequest(1L, 10L, new BigDecimal("65000"))));

    assertThatThrownBy(() -> calculationService.calculate(request))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_NOT_FOUND);
  }

  private Asset asset(
      Long id, String code, String name, AssetCategory category, StockMarket market) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(id);
    given(asset.getAssetCode()).willReturn(code);
    given(asset.getName()).willReturn(name);
    given(asset.getCategory()).willReturn(category);
    given(asset.getMarket()).willReturn(market);
    return asset;
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }
}
