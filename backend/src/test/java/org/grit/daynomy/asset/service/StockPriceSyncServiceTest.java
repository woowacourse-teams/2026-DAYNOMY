package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.inOrder;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
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
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class StockPriceSyncServiceTest {

  @Mock private PublicDataStockPriceClient stockPriceClient;
  @Mock private PublicDataEtfPriceClient etfPriceClient;
  @Mock private StockPricePersistenceService persistenceService;
  @Mock private StockPriceSyncMetrics syncMetrics;
  @Mock private ApplicationEventPublisher eventPublisher;
  @Mock private MarketTradingDayRepository tradingDayRepository;
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
  @DisplayName("미수집 거래일의 KOSPI·KOSDAQ 종가를 전체 페이지에서 동기화한다")
  void synchronizeLatestStockPrices() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate baseDate = today.minusDays(3);
    LocalDate previousBaseDate = baseDate.minusDays(1);
    stubMissingDays(today, baseDate, previousBaseDate);
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

    assertThat(result.receivedCount()).isEqualTo(7);
    assertThat(result.synchronizedDateCount()).isEqualTo(2);
    var order = inOrder(persistenceService, eventPublisher);
    order.verify(persistenceService).synchronize(eq(previousBaseDate), anyList());
    order.verify(persistenceService).synchronize(eq(baseDate), anyList());
    order.verify(eventPublisher).publishEvent(result.withEarliestBaseDate(previousBaseDate));
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
        .containsEntry("receivedCount", 7)
        .containsEntry("createdCount", 7)
        .containsEntry("updatedCount", 0)
        .containsEntry("skippedCount", 0)
        .containsKey("durationMs");
  }

  @Test
  @DisplayName("시장별 응답 항목 수가 전체 건수보다 적으면 불완전한 종가 스냅샷을 저장하지 않는다")
  void synchronizeRejectsIncompleteMarketSnapshot() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate missed = today.minusDays(3);
    stubMissingDays(today, missed);
    given(stockPriceClient.getStockPrices(missed, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(2, List.of(item(missed, "005930", "82000"))));

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    then(persistenceService).shouldHaveNoInteractions();
    then(eventPublisher).shouldHaveNoInteractions();

    ILoggingEvent failedLog = logFor(LogEvent.STOCK_PRICE_SYNC_FAILED);
    assertThat(failedLog.getLevel()).isEqualTo(Level.ERROR);
    assertThat(keyValues(failedLog))
        .containsEntry("errorCode", AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND.code())
        .containsKey("durationMs");
  }

  @Test
  @DisplayName("ETF 수집이 늦은 날짜는 수집 완료로 저장하지 않는다")
  void incompleteEtfSnapshotRemainsPending() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate missed = today.minusDays(3);
    LocalDate older = today.minusDays(4);
    stubMissingDays(today, missed, older);
    given(stockPriceClient.getStockPrices(missed, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(1, List.of(item(missed, "005930", "82000"))));
    given(stockPriceClient.getStockPrices(missed, StockMarket.KOSDAQ, 1, 1000))
        .willReturn(response(1, List.of(item(missed, "247540", "285000"))));
    given(etfPriceClient.getEtfPrices(missed, 1, 1000)).willReturn(etfResponse(0, List.of()));
    stubCompleteSnapshot(older);
    stubPersistenceResult();

    StockPriceSyncResult result = syncService.synchronize(today);

    assertThat(result.baseDate()).isEqualTo(older);
    then(persistenceService).should(org.mockito.Mockito.never()).synchronize(eq(missed), anyList());
    then(eventPublisher).should().publishEvent(result.withEarliestBaseDate(older));
  }

  @Test
  @DisplayName("10일보다 오래된 미수집 거래일도 복구하고 그 날짜부터 재집계한다")
  void recoversEarlierMissingTradingDay() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate missed = today.minusDays(20);
    LocalDate latest = today.minusDays(3);
    stubMissingDays(today, latest, missed);
    stubCompleteSnapshot(latest);
    stubCompleteSnapshot(missed);
    stubPersistenceResult();

    StockPriceSyncResult result = syncService.synchronize(today);

    var order = inOrder(persistenceService, eventPublisher);
    order.verify(persistenceService).synchronize(eq(missed), anyList());
    order.verify(persistenceService).synchronize(eq(latest), anyList());
    order.verify(eventPublisher).publishEvent(result.withEarliestBaseDate(missed));
  }

  @Test
  @DisplayName("최신 미수집 10일이 계속 비어 있어도 이전 날짜를 다음 실행에서 자동 수집한다")
  void failedRecentDatesDoNotBlockOlderMissingDate() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    List<LocalDate> missing = new ArrayList<>();
    for (LocalDate date = today.minusDays(3); missing.size() < 12; date = date.minusDays(1)) {
      if (date.getDayOfWeek().getValue() <= 5) missing.add(date);
    }
    LocalDate oldest = missing.getLast();
    stubMissingDays(today, missing.toArray(LocalDate[]::new));
    missing.stream().filter(date -> !date.equals(oldest)).forEach(this::stubEmptyDay);
    stubCompleteSnapshot(oldest);
    stubPersistenceResult();

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    then(stockPriceClient)
        .should(org.mockito.Mockito.never())
        .getStockPrices(oldest, StockMarket.KOSPI, 1, 1000);

    StockPriceSyncResult result = syncService.synchronize(today);

    assertThat(result.baseDate()).isEqualTo(oldest);
    then(stockPriceClient).should().getStockPrices(oldest, StockMarket.KOSPI, 1, 1000);
    then(eventPublisher).should().publishEvent(result.withEarliestBaseDate(oldest));
  }

  @Test
  @DisplayName("한 날짜의 API 오류가 다른 미수집 날짜의 자동 수집을 막지 않는다")
  void failedDateDoesNotBlockOtherMissingDate() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate latest = today.minusDays(3);
    LocalDate older = today.minusDays(4);
    stubMissingDays(today, latest, older);
    given(stockPriceClient.getStockPrices(latest, StockMarket.KOSPI, 1, 1000))
        .willThrow(new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED));
    stubCompleteSnapshot(older);
    stubPersistenceResult();

    StockPriceSyncResult result = syncService.synchronize(today);

    assertThat(result.baseDate()).isEqualTo(older);
    assertThat(result.synchronizedDateCount()).isEqualTo(1);
    assertThat(result.failedDateCount()).isEqualTo(1);
    then(eventPublisher).should().publishEvent(result.withEarliestBaseDate(older));
  }

  @Test
  @DisplayName("이미 수집한 날짜와 휴장일은 재요청하지 않고 미제공 누락일은 다음 실행에 남긴다")
  void skipsCollectedAndClosedDatesAndKeepsUnavailableDatePending() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate latest = today.minusDays(3);
    LocalDate previous = today.minusDays(4);
    LocalDate missed = today.minusDays(5);
    stubMissingDays(today, latest, previous, missed);
    stubCompleteSnapshot(latest);
    stubCompleteSnapshot(previous);
    stubEmptyDay(missed);
    stubPersistenceResult();

    StockPriceSyncResult result = syncService.synchronize(today);

    then(stockPriceClient)
        .should(org.mockito.Mockito.never())
        .getStockPrices(
            eq(today.minusDays(6)),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt());
    then(stockPriceClient)
        .should(org.mockito.Mockito.never())
        .getStockPrices(
            eq(today.minusDays(7)),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt());
    then(persistenceService)
        .should(org.mockito.Mockito.times(2))
        .synchronize(org.mockito.ArgumentMatchers.any(), anyList());
    then(eventPublisher).should().publishEvent(result.withEarliestBaseDate(previous));
  }

  @Test
  @DisplayName("일부 날짜의 종가 저장이 실패해도 커밋된 날짜를 재집계한다")
  void synchronizeRecalculatesCommittedDatesAfterPersistenceFailure() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate latest = today.minusDays(3);
    LocalDate previousBaseDate = today.minusDays(4);
    stubMissingDays(today, latest, previousBaseDate);
    stubCompleteSnapshot(latest);
    stubCompleteSnapshot(previousBaseDate);
    given(
            persistenceService.synchronize(
                org.mockito.ArgumentMatchers.any(LocalDate.class),
                org.mockito.ArgumentMatchers.anyList()))
        .willAnswer(
            invocation -> {
              LocalDate baseDate = invocation.getArgument(0);
              if (baseDate.equals(latest)) {
                throw new IllegalStateException("database failure");
              }
              List<?> entries = invocation.getArgument(1);
              return new StockPriceSyncResult(baseDate, entries.size(), entries.size(), 0, 0);
            });

    given(
            tradingDayRepository.countByTradingTrueAndCollectedAtIsNullAndBaseDateLessThanEqual(
                today))
        .willReturn(1L);

    StockPriceSyncResult result = syncService.synchronize(today);

    assertThat(result.baseDate()).isEqualTo(previousBaseDate);
    assertThat(result.earliestBaseDate()).isEqualTo(previousBaseDate);
    assertThat(result.synchronizedDateCount()).isEqualTo(1);
    assertThat(result.pendingDateCount()).isEqualTo(1);
    assertThat(result.failedDateCount()).isEqualTo(1);
    then(eventPublisher).should().publishEvent(result);
    assertThat(appender.list)
        .anySatisfy(
            event -> {
              assertThat(event.getFormattedMessage()).contains("종가 저장 실패");
              assertThat(event.getFormattedMessage()).doesNotContain("database failure");
            });
  }

  @Test
  @DisplayName("모든 날짜의 종가 저장이 실패하면 실패를 반환하고 재집계하지 않는다")
  void synchronizeFailsWhenNoDateWasSaved() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate missed = today.minusDays(3);
    stubMissingDays(today, missed);
    stubCompleteSnapshot(missed);
    given(persistenceService.synchronize(eq(missed), anyList()))
        .willThrow(new IllegalStateException("database failure"));

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(IllegalStateException.class);
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("외부 종가 API 호출이 실패하면 전체 동기화 실패를 기록한다")
  void synchronizeLogsExternalApiFailure() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate missed = today.minusDays(3);
    stubMissingDays(today, missed);
    given(stockPriceClient.getStockPrices(missed, StockMarket.KOSPI, 1, 1000))
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
  @DisplayName("수집 가능한 미수집 거래일에 종가 데이터가 없으면 동기화를 실패로 기록한다")
  void synchronizeThrowsWhenSnapshotIsMissing() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate missed = today.minusDays(3);
    stubMissingDays(today, missed);
    stubEmptyDay(missed);

    assertThatThrownBy(() -> syncService.synchronize(today))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    then(persistenceService).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("미수집 거래일이 없으면 외부 API를 호출하거나 재집계를 시작하지 않는다")
  void synchronizeDoesNothingWhenAllPricesAreCollected() {
    LocalDate today = LocalDate.of(2026, 9, 21);
    var latest = new MarketTradingDay(today.minusDays(3), true);
    latest.collected(java.time.Instant.now());
    given(
            tradingDayRepository
                .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                    today))
        .willReturn(java.util.Optional.of(latest));

    StockPriceSyncResult result = syncService.synchronize(today);

    assertThat(result.baseDate()).isEqualTo(latest.getBaseDate());
    assertThat(result.receivedCount()).isZero();
    then(stockPriceClient).shouldHaveNoInteractions();
    then(etfPriceClient).shouldHaveNoInteractions();
    then(persistenceService).shouldHaveNoInteractions();
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("다음 거래일 13시 전에 종가를 요청하지 않는다")
  void waitsUntilNextTradingDayPublication(
      @Mock(answer = org.mockito.Answers.CALLS_REAL_METHODS)
          MockedStatic<java.time.Instant> instants) {
    LocalDate today = LocalDate.of(2026, 9, 21);
    LocalDate friday = today.minusDays(3);
    stubMissingDays(today, friday);
    given(
            tradingDayRepository.countByTradingTrueAndCollectedAtIsNullAndBaseDateLessThanEqual(
                today))
        .willReturn(1L);
    var beforePublication = today.atTime(12, 59).atZone(ZoneId.of("Asia/Seoul")).toInstant();
    instants.when(java.time.Instant::now).thenReturn(beforePublication);

    StockPriceSyncResult result = syncService.synchronize(today);

    assertThat(result.receivedCount()).isZero();
    assertThat(result.pendingDateCount()).isEqualTo(1);
    then(stockPriceClient).shouldHaveNoInteractions();
    then(persistenceService).shouldHaveNoInteractions();
    then(eventPublisher).shouldHaveNoInteractions();
  }

  @Test
  @DisplayName("지정한 기간의 거래일 종가만 백필한다")
  void backfillStockPrices() {
    LocalDate from = LocalDate.of(2026, 9, 18);
    LocalDate to = LocalDate.of(2026, 9, 20);
    stubCompleteSnapshot(from);
    stubCompleteSnapshot(from.plusDays(1));
    stubEmptyDay(to);
    stubPersistenceResult();

    StockPriceBackfillResult result = syncService.backfill(from, to);

    assertThat(result.synchronizedDateCount()).isEqualTo(2);
    assertThat(result.createdCount()).isEqualTo(6);
    then(persistenceService)
        .should()
        .synchronize(org.mockito.ArgumentMatchers.eq(from), org.mockito.ArgumentMatchers.anyList());
    then(syncMetrics).should().recordSuccess(org.mockito.ArgumentMatchers.any());
    var order = inOrder(persistenceService, eventPublisher);
    order.verify(persistenceService).synchronize(eq(from), anyList());
    order.verify(persistenceService).synchronize(eq(from.plusDays(1)), anyList());
    order
        .verify(eventPublisher)
        .publishEvent(new StockPriceSyncResult(from.plusDays(1), 3, 3, 0, 0, from));
  }

  @Test
  @DisplayName("기간별 백필 중 나중 날짜가 실패해도 이미 저장한 종가는 재집계한다")
  void backfillRecalculatesCommittedDateAfterFailure() {
    LocalDate first = LocalDate.of(2026, 9, 18);
    LocalDate second = first.plusDays(1);
    stubCompleteSnapshot(first);
    stubCompleteSnapshot(second);
    given(persistenceService.synchronize(eq(first), anyList()))
        .willReturn(new StockPriceSyncResult(first, 3, 3, 0, 0));
    given(persistenceService.synchronize(eq(second), anyList()))
        .willThrow(new IllegalStateException("database failure"));

    assertThatThrownBy(() -> syncService.backfill(first, second))
        .isInstanceOf(IllegalStateException.class);
    then(eventPublisher).should().publishEvent(new StockPriceSyncResult(first, 3, 3, 0, 0));
  }

  private void stubEmptyDay(LocalDate date) {
    given(stockPriceClient.getStockPrices(date, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(0, List.of()));
  }

  private void stubMissingDays(LocalDate today, LocalDate... dates) {
    var missing = java.util.Set.of(dates);
    List<MarketTradingDay> calendar = new ArrayList<>();
    LocalDate first = java.util.Arrays.stream(dates).min(LocalDate::compareTo).orElseThrow();
    for (LocalDate date = first; !date.isAfter(today); date = date.plusDays(1)) {
      if (date.getDayOfWeek().getValue() > 5) continue;
      MarketTradingDay day = new MarketTradingDay(date, true);
      if (!missing.contains(date)) day.collected(java.time.Instant.now());
      calendar.add(day);
    }
    given(tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(today))
        .willReturn(calendar);
  }

  private void stubCompleteSnapshot(LocalDate date) {
    given(stockPriceClient.getStockPrices(date, StockMarket.KOSPI, 1, 1000))
        .willReturn(response(1, List.of(item(date, "005930", "80000"))));
    given(stockPriceClient.getStockPrices(date, StockMarket.KOSDAQ, 1, 1000))
        .willReturn(response(1, List.of(item(date, "247540", "280000"))));
    given(etfPriceClient.getEtfPrices(date, 1, 1000))
        .willReturn(etfResponse(1, List.of(etfItem(date, "069500", "KODEX 200", "53000"))));
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
