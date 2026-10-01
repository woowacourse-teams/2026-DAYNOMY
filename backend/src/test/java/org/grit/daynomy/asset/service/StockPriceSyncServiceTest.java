package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.publicdata.PublicDataEtfPriceClient;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataEtfPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataEtfPriceResponse;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

@ExtendWith(MockitoExtension.class)
class StockPriceSyncServiceTest {

  @Mock private PublicDataStockPriceClient stockPriceClient;
  @Mock private PublicDataEtfPriceClient etfPriceClient;
  @Mock private StockPricePersistenceService persistenceService;
  @InjectMocks private StockPriceSyncService syncService;

  private final Logger logger = (Logger) LoggerFactory.getLogger(StockPriceSyncService.class);
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
  @DisplayName("최근 2개 거래일의 KOSPI·KOSDAQ 종가를 전체 페이지에서 동기화한다")
  void synchronizeLatestStockPrices() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate baseDate = today.minusDays(3);
    LocalDate previousBaseDate = baseDate.minusDays(1);
    stubEmptyDay(today);
    stubEmptyDay(today.minusDays(1));
    stubEmptyDay(today.minusDays(2));
    given(stockPriceClient.getStockPrices(baseDate, StockMarket.KOSPI, 1, 1000))
        .willReturn(
            response(
                1001,
                fullPage(
                    baseDate,
                    List.of(item(baseDate, "005930", "82000"), item(baseDate, "잘못된코드", "1000")))));
    given(stockPriceClient.getStockPrices(baseDate, StockMarket.KOSPI, 2, 1000))
        .willReturn(response(1001, List.of(item(baseDate, "000660", "190000"))));
    given(stockPriceClient.getStockPrices(baseDate, StockMarket.KOSDAQ, 1, 1000))
        .willReturn(response(1, List.of(item(baseDate, "247540", "285000"))));
    given(etfPriceClient.getEtfPrices(baseDate, 1, 1000))
        .willReturn(etfResponse(1, List.of(etfItem(baseDate, "069500", "KODEX 200", "53000"))));
    stubCompleteSnapshot(previousBaseDate);
    stubPersistenceResult();

    StockPriceSyncResult result = syncService.synchronize(today);

    assertThat(result.receivedCount()).isEqualTo(4);
    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockPriceEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
    then(persistenceService)
        .should()
        .synchronize(org.mockito.ArgumentMatchers.eq(baseDate), entriesCaptor.capture());
    then(persistenceService)
        .should()
        .synchronize(
            org.mockito.ArgumentMatchers.eq(previousBaseDate),
            org.mockito.ArgumentMatchers.anyList());
    assertThat(entriesCaptor.getValue())
        .extracting(
            StockPriceEntry::assetCode, StockPriceEntry::category, StockPriceEntry::closePrice)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple(
                "005930", AssetCategory.STOCK, new java.math.BigDecimal("82000")),
            org.assertj.core.groups.Tuple.tuple(
                "000660", AssetCategory.STOCK, new java.math.BigDecimal("190000")),
            org.assertj.core.groups.Tuple.tuple(
                "247540", AssetCategory.STOCK, new java.math.BigDecimal("285000")),
            org.assertj.core.groups.Tuple.tuple(
                "069500", AssetCategory.ETF, new java.math.BigDecimal("53000")));

    ILoggingEvent completedLog = logFor(LogEvent.STOCK_PRICE_SYNC_COMPLETED);
    assertThat(completedLog.getLevel()).isEqualTo(Level.INFO);
    assertThat(keyValues(completedLog))
        .containsEntry("baseDate", baseDate)
        .containsEntry("receivedCount", 4)
        .containsEntry("createdCount", 4)
        .containsEntry("updatedCount", 0)
        .containsEntry("skippedCount", 0)
        .containsKey("durationMs");
  }

  @Test
  @DisplayName("시장별 응답 항목 수가 전체 건수보다 적으면 불완전한 종가 스냅샷을 저장하지 않는다")
  void synchronizeRejectsIncompleteMarketSnapshot() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    given(stockPriceClient.getStockPrices(today, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(2, List.of(item(today, "005930", "82000"))));

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    then(persistenceService).shouldHaveNoInteractions();

    ILoggingEvent failedLog = logFor(LogEvent.STOCK_PRICE_SYNC_FAILED);
    assertThat(failedLog.getLevel()).isEqualTo(Level.ERROR);
    assertThat(keyValues(failedLog))
        .containsEntry("errorCode", AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND.code())
        .containsKey("durationMs");
  }

  @Test
  @DisplayName("ETF 데이터가 없어도 주식 종가를 동기화한다")
  void synchronizeStockPricesWithoutEtfData() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate previousBaseDate = today.minusDays(1);
    given(stockPriceClient.getStockPrices(today, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(1, List.of(item(today, "005930", "82000"))));
    given(stockPriceClient.getStockPrices(today, StockMarket.KOSDAQ, 1, 1000))
        .willReturn(response(1, List.of(item(today, "247540", "285000"))));
    given(etfPriceClient.getEtfPrices(today, 1, 1000)).willReturn(etfResponse(0, List.of()));
    stubCompleteSnapshot(previousBaseDate);
    stubPersistenceResult();

    syncService.synchronize(today);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<StockPriceEntry>> entriesCaptor = ArgumentCaptor.forClass(List.class);
    then(persistenceService)
        .should()
        .synchronize(org.mockito.ArgumentMatchers.eq(today), entriesCaptor.capture());
    assertThat(entriesCaptor.getValue())
        .extracting(StockPriceEntry::assetCode, StockPriceEntry::category)
        .containsExactly(
            org.assertj.core.groups.Tuple.tuple("005930", AssetCategory.STOCK),
            org.assertj.core.groups.Tuple.tuple("247540", AssetCategory.STOCK));
  }

  @Test
  @DisplayName("종가 저장 중 예상하지 못한 오류가 발생하면 동기화 실패를 기록한다")
  void synchronizeLogsUnexpectedPersistenceFailure() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate previousBaseDate = today.minusDays(1);
    given(stockPriceClient.getStockPrices(today, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(1, List.of(item(today, "005930", "82000"))));
    given(stockPriceClient.getStockPrices(today, StockMarket.KOSDAQ, 1, 1000))
        .willReturn(response(1, List.of(item(today, "247540", "285000"))));
    given(etfPriceClient.getEtfPrices(today, 1, 1000)).willReturn(etfResponse(0, List.of()));
    stubCompleteSnapshot(previousBaseDate);
    given(
            persistenceService.synchronize(
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.anyList()))
        .willAnswer(
            invocation -> {
              LocalDate baseDate = invocation.getArgument(0);
              if (baseDate.equals(today)) {
                throw new IllegalStateException("database failure");
              }
              List<?> entries = invocation.getArgument(1);
              return new StockPriceSyncResult(baseDate, entries.size(), entries.size(), 0, 0);
            });

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(IllegalStateException.class);

    ILoggingEvent failedLog = logFor(LogEvent.STOCK_PRICE_SYNC_FAILED);
    assertThat(failedLog.getLevel()).isEqualTo(Level.ERROR);
    assertThat(keyValues(failedLog))
        .containsEntry("exception", IllegalStateException.class.getSimpleName())
        .containsKey("durationMs");
    assertThat(failedLog.getFormattedMessage()).doesNotContain("database failure");
  }

  @Test
  @DisplayName("외부 종가 API 호출이 실패하면 전체 동기화 실패를 기록한다")
  void synchronizeLogsExternalApiFailure() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    given(stockPriceClient.getStockPrices(today, StockMarket.KOSPI, 1, 1000))
        .willThrow(new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED));

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);

    ILoggingEvent failedLog = logFor(LogEvent.STOCK_PRICE_SYNC_FAILED);
    assertThat(failedLog.getLevel()).isEqualTo(Level.ERROR);
    assertThat(keyValues(failedLog))
        .containsEntry("errorCode", ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED.code())
        .containsKey("durationMs");
  }

  @Test
  @DisplayName("최근 10일 동안 양 시장의 종가 데이터가 없으면 동기화를 중단한다")
  void synchronizeThrowsWhenSnapshotIsMissing() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    for (int daysAgo = 0; daysAgo <= 10; daysAgo++) {
      stubEmptyDay(today.minusDays(daysAgo));
    }

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    then(persistenceService).shouldHaveNoInteractions();
  }

  private void stubEmptyDay(LocalDate date) {
    given(stockPriceClient.getStockPrices(date, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(0, List.of()));
  }

  private void stubCompleteSnapshot(LocalDate date) {
    given(stockPriceClient.getStockPrices(date, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(1, List.of(item(date, "005930", "80000"))));
    given(stockPriceClient.getStockPrices(date, StockMarket.KOSDAQ, 1, 1000))
        .willReturn(response(1, List.of(item(date, "247540", "280000"))));
    given(etfPriceClient.getEtfPrices(date, 1, 1000)).willReturn(etfResponse(0, List.of()));
  }

  private void stubPersistenceResult() {
    given(
            persistenceService.synchronize(
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.anyList()))
        .willAnswer(
            invocation -> {
              LocalDate baseDate = invocation.getArgument(0);
              List<?> entries = invocation.getArgument(1);
              return new StockPriceSyncResult(baseDate, entries.size(), entries.size(), 0, 0);
            });
  }

  private PublicDataStockPriceResponse response(
      int totalCount, List<PublicDataStockPriceItem> items) {
    return new PublicDataStockPriceResponse(
        new PublicDataStockPriceResponse.Response(
            new PublicDataStockPriceResponse.Header("00", "NORMAL SERVICE."),
            new PublicDataStockPriceResponse.Body(
                1000, 1, totalCount, new PublicDataStockPriceResponse.Items(items))));
  }

  private PublicDataEtfPriceResponse etfResponse(
      int totalCount, List<PublicDataEtfPriceItem> items) {
    return new PublicDataEtfPriceResponse(
        new PublicDataEtfPriceResponse.Response(
            new PublicDataEtfPriceResponse.Header("00", "NORMAL SERVICE."),
            new PublicDataEtfPriceResponse.Body(
                1000, 1, totalCount, new PublicDataEtfPriceResponse.Items(items))));
  }

  private PublicDataEtfPriceItem etfItem(
      LocalDate baseDate, String code, String name, String closePrice) {
    return new PublicDataEtfPriceItem(
        baseDate.format(DateTimeFormatter.BASIC_ISO_DATE),
        code,
        "KR7" + code + "007",
        name,
        closePrice);
  }

  private PublicDataStockPriceItem item(LocalDate baseDate, String code, String closePrice) {
    return new PublicDataStockPriceItem(
        baseDate.format(DateTimeFormatter.BASIC_ISO_DATE),
        code,
        "테스트종목",
        "KOSPI",
        closePrice,
        "1000000000");
  }

  private List<PublicDataStockPriceItem> fullPage(
      LocalDate baseDate, List<PublicDataStockPriceItem> leadingItems) {
    List<PublicDataStockPriceItem> items = new ArrayList<>(leadingItems);
    PublicDataStockPriceItem invalidItem = item(baseDate, "잘못된코드", "1000");
    while (items.size() < 1000) {
      items.add(invalidItem);
    }
    return items;
  }

  private ILoggingEvent logFor(LogEvent event) {
    return appender.list.stream()
        .filter(candidate -> event.code().equals(keyValues(candidate).get("event")))
        .findFirst()
        .orElseThrow();
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }
}
