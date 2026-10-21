package org.grit.daynomy.asset.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.MarketTradingDay;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.publicdata.PublicDataEtfPriceClient;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataEtfPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataEtfPriceResponse;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StockPriceSyncService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private static final int PAGE_SIZE = 1000;
  private static final Pattern STOCK_CODE = Pattern.compile("\\d{6}");

  private final PublicDataStockPriceClient stockPriceClient;
  private final PublicDataEtfPriceClient etfPriceClient;
  private final StockPricePersistenceService persistenceService;
  private final StockPriceSyncMetrics syncMetrics;
  private final ApplicationEventPublisher eventPublisher;
  private final MarketTradingDayRepository tradingDayRepository;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public StockPriceSyncService(
      PublicDataStockPriceClient stockPriceClient,
      PublicDataEtfPriceClient etfPriceClient,
      StockPricePersistenceService persistenceService,
      StockPriceSyncMetrics syncMetrics,
      ApplicationEventPublisher eventPublisher,
      MarketTradingDayRepository tradingDayRepository) {
    this.stockPriceClient = stockPriceClient;
    this.etfPriceClient = etfPriceClient;
    this.persistenceService = persistenceService;
    this.syncMetrics = syncMetrics;
    this.eventPublisher = eventPublisher;
    this.tradingDayRepository = tradingDayRepository;
  }

  public StockPriceSyncResult synchronize() {
    return synchronize(LocalDate.now(SEOUL));
  }

  StockPriceSyncResult synchronize(LocalDate today) {
    if (!running.compareAndSet(false, true)) {
      log.atWarn()
          .addKeyValue("event", LogEvent.STOCK_PRICE_SYNC_SKIPPED.code())
          .addKeyValue("reasonCode", AssetErrorCode.STOCK_PRICE_SYNC_ALREADY_RUNNING.code())
          .log(LogEvent.STOCK_PRICE_SYNC_SKIPPED.message());
      throw new BusinessException(AssetErrorCode.STOCK_PRICE_SYNC_ALREADY_RUNNING);
    }

    long startedAt = System.nanoTime();
    log.atDebug()
        .addKeyValue("event", LogEvent.STOCK_PRICE_SYNC_STARTED.code())
        .addKeyValue("requestedDate", today)
        .log(LogEvent.STOCK_PRICE_SYNC_STARTED.message());

    try {
      StockPriceSelection selection = findSnapshotsToSynchronize(today);
      List<StockPriceSnapshot> snapshots = selection.snapshots();
      if (snapshots.isEmpty()) {
        LocalDate latestCollected =
            tradingDayRepository
                .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                    today)
                .map(MarketTradingDay::getBaseDate)
                .orElse(null);
        StockPriceSyncResult empty =
            new StockPriceSyncResult(
                latestCollected,
                0,
                0,
                0,
                0,
                latestCollected,
                0,
                tradingDayRepository.countByTradingTrueAndCollectedAtIsNullAndBaseDateLessThanEqual(
                    today),
                0);
        if (latestCollected != null) syncMetrics.recordSuccess(empty);
        return empty;
      }
      StockPriceSyncResult latest = null;
      LocalDate earliest = null;
      int synchronizedDates = 0;
      int received = 0;
      int created = 0;
      int updated = 0;
      int skipped = 0;
      int failed = selection.failedDateCount();
      RuntimeException firstPersistenceFailure = null;
      for (StockPriceSnapshot snapshot : snapshots) {
        try {
          StockPriceSyncResult saved =
              persistenceService.synchronize(snapshot.baseDate(), snapshot.entries());
          latest = saved;
          if (earliest == null) earliest = snapshot.baseDate();
          synchronizedDates++;
          received += saved.receivedCount();
          created += saved.createdCount();
          updated += saved.updatedCount();
          skipped += saved.skippedCount();
        } catch (RuntimeException exception) {
          if (firstPersistenceFailure == null) firstPersistenceFailure = exception;
          failed++;
          log.atWarn()
              .addKeyValue("baseDate", snapshot.baseDate())
              .addKeyValue("exception", exception.getClass().getSimpleName())
              .log("종가 저장 실패, 다음 실행에서 재시도합니다.");
        }
      }
      if (latest == null) throw firstPersistenceFailure;
      StockPriceSyncResult result =
          new StockPriceSyncResult(
              latest.baseDate(),
              received,
              created,
              updated,
              skipped,
              earliest,
              synchronizedDates,
              tradingDayRepository.countByTradingTrueAndCollectedAtIsNullAndBaseDateLessThanEqual(
                  today),
              failed);
      syncMetrics.recordSuccess(result);
      log.atInfo()
          .addKeyValue("event", LogEvent.STOCK_PRICE_SYNC_COMPLETED.code())
          .addKeyValue("baseDate", result.baseDate())
          .addKeyValue("receivedCount", result.receivedCount())
          .addKeyValue("createdCount", result.createdCount())
          .addKeyValue("updatedCount", result.updatedCount())
          .addKeyValue("skippedCount", result.skippedCount())
          .addKeyValue("synchronizedDateCount", result.synchronizedDateCount())
          .addKeyValue("pendingDateCount", result.pendingDateCount())
          .addKeyValue("failedDateCount", result.failedDateCount())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.STOCK_PRICE_SYNC_COMPLETED.message());
      // 각 날짜의 저장 트랜잭션이 커밋된 뒤에만 후속 집계를 실행한다.
      eventPublisher.publishEvent(result);
      return result;
    } catch (BusinessException exception) {
      log.atError()
          .addKeyValue("event", LogEvent.STOCK_PRICE_SYNC_FAILED.code())
          .addKeyValue("errorCode", exception.errorCode().code())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.STOCK_PRICE_SYNC_FAILED.message());
      throw exception;
    } catch (RuntimeException exception) {
      log.atError()
          .addKeyValue("event", LogEvent.STOCK_PRICE_SYNC_FAILED.code())
          .addKeyValue("exception", exception.getClass().getSimpleName())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.STOCK_PRICE_SYNC_FAILED.message());
      throw exception;
    } finally {
      running.set(false);
    }
  }

  public StockPriceBackfillResult backfill(LocalDate from, LocalDate to) {
    if (from == null || to == null || from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 30) {
      throw new BusinessException(AssetErrorCode.INVALID_STOCK_PRICE_BACKFILL_RANGE);
    }
    if (!running.compareAndSet(false, true)) {
      throw new BusinessException(AssetErrorCode.STOCK_PRICE_SYNC_ALREADY_RUNNING);
    }
    int synchronizedDates = 0;
    int received = 0;
    int created = 0;
    int updated = 0;
    int skipped = 0;
    StockPriceSyncResult latest = null;
    LocalDate earliest = null;
    try {
      for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
        Optional<StockPriceSnapshot> snapshot = findSnapshot(date);
        if (snapshot.isEmpty()) {
          continue;
        }
        StockPriceSyncResult result =
            persistenceService.synchronize(date, snapshot.get().entries());
        synchronizedDates++;
        if (earliest == null) earliest = date;
        received += result.receivedCount();
        created += result.createdCount();
        updated += result.updatedCount();
        skipped += result.skippedCount();
        if (latest == null || result.baseDate().isAfter(latest.baseDate())) {
          latest = result;
        }
      }
      return new StockPriceBackfillResult(
          from, to, synchronizedDates, received, created, updated, skipped);
    } finally {
      try {
        if (latest != null) {
          syncMetrics.recordSuccess(latest);
          eventPublisher.publishEvent(latest.withEarliestBaseDate(earliest));
        }
      } finally {
        running.set(false);
      }
    }
  }

  private StockPriceSelection findSnapshotsToSynchronize(LocalDate today) {
    List<StockPriceSnapshot> snapshots = new ArrayList<>();
    Instant now = Instant.now();
    List<MarketTradingDay> calendar =
        tradingDayRepository.findAllByTradingTrueAndBaseDateLessThanEqualOrderByBaseDate(today);
    List<MarketTradingDay> eligible = new ArrayList<>();
    for (int index = 0; index < calendar.size() - 1; index++) {
      MarketTradingDay day = calendar.get(index);
      MarketTradingDay next = calendar.get(index + 1);
      if (day.getCollectedAt() == null
          && !now.isBefore(next.getBaseDate().atTime(14, 0).atZone(SEOUL).toInstant())) {
        eligible.add(day);
      }
    }
    if (eligible.isEmpty()) return new StockPriceSelection(snapshots, 0);

    List<MarketTradingDay> selected = new ArrayList<>();
    selected.add(eligible.getLast());
    eligible.subList(0, eligible.size() - 1).stream()
        .sorted(
            Comparator.comparing(
                    MarketTradingDay::getLastAttemptedAt,
                    Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(MarketTradingDay::getBaseDate, Comparator.reverseOrder()))
        .limit(9)
        .forEach(selected::add);
    BusinessException failure = null;
    for (MarketTradingDay day : selected) {
      try {
        findSnapshot(day.getBaseDate()).ifPresent(snapshots::add);
      } catch (BusinessException exception) {
        if (failure == null) failure = exception;
        log.atWarn()
            .addKeyValue("baseDate", day.getBaseDate())
            .addKeyValue("errorCode", exception.errorCode().code())
            .log("종가 수집 실패, 다음 실행에서 재시도합니다.");
      } finally {
        day.attempted(now);
        tradingDayRepository.save(day);
      }
    }
    if (snapshots.isEmpty() && failure != null) throw failure;
    if (snapshots.isEmpty()) throw new BusinessException(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    snapshots.sort(Comparator.comparing(StockPriceSnapshot::baseDate));
    return new StockPriceSelection(snapshots, selected.size() - snapshots.size());
  }

  private Optional<StockPriceSnapshot> findSnapshot(LocalDate requestedDate) {
    List<PublicDataStockPriceItem> items = new ArrayList<>();
    for (StockMarket market : List.of(StockMarket.KOSPI, StockMarket.KOSDAQ)) {
      List<PublicDataStockPriceItem> marketItems = getAllPages(requestedDate, market);
      if (marketItems.isEmpty()) {
        return Optional.empty();
      }
      items.addAll(marketItems);
    }
    List<PublicDataEtfPriceItem> etfItems = getAllEtfPages(requestedDate);
    if (etfItems.isEmpty()) return Optional.empty();
    List<StockPriceEntry> entries = new ArrayList<>(toStockEntries(items, requestedDate));
    entries.addAll(toEtfEntries(etfItems, requestedDate));
    if (entries.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new StockPriceSnapshot(requestedDate, List.copyOf(entries)));
  }

  private List<PublicDataStockPriceItem> getAllPages(LocalDate baseDate, StockMarket market) {
    PublicDataStockPriceResponse firstPage =
        stockPriceClient.getStockPrices(baseDate, market, 1, PAGE_SIZE);
    if (firstPage.body().totalCount() == 0 || firstPage.items().isEmpty()) {
      return List.of();
    }

    List<PublicDataStockPriceItem> items = new ArrayList<>(firstPage.items());
    int totalPages = (firstPage.body().totalCount() + PAGE_SIZE - 1) / PAGE_SIZE;
    for (int page = 2; page <= totalPages; page++) {
      PublicDataStockPriceResponse nextPage =
          stockPriceClient.getStockPrices(baseDate, market, page, PAGE_SIZE);
      if (nextPage.items().isEmpty()) {
        throw new BusinessException(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
      }
      items.addAll(nextPage.items());
    }
    if (items.size() < firstPage.body().totalCount()) {
      throw new BusinessException(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    }
    return items;
  }

  private List<PublicDataEtfPriceItem> getAllEtfPages(LocalDate baseDate) {
    PublicDataEtfPriceResponse firstPage = etfPriceClient.getEtfPrices(baseDate, 1, PAGE_SIZE);
    if (firstPage.body().totalCount() == 0 || firstPage.items().isEmpty()) {
      return List.of();
    }

    List<PublicDataEtfPriceItem> items = new ArrayList<>(firstPage.items());
    int totalPages = (firstPage.body().totalCount() + PAGE_SIZE - 1) / PAGE_SIZE;
    for (int page = 2; page <= totalPages; page++) {
      PublicDataEtfPriceResponse nextPage = etfPriceClient.getEtfPrices(baseDate, page, PAGE_SIZE);
      if (nextPage.items().isEmpty()) {
        throw new BusinessException(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
      }
      items.addAll(nextPage.items());
    }
    if (items.size() < firstPage.body().totalCount()) {
      throw new BusinessException(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    }
    return items;
  }

  private List<StockPriceEntry> toStockEntries(
      List<PublicDataStockPriceItem> items, LocalDate requestedDate) {
    Map<String, StockPriceEntry> entriesByCode = new LinkedHashMap<>();
    for (PublicDataStockPriceItem item : items) {
      toStockEntry(item, requestedDate)
          .ifPresent(entry -> entriesByCode.put(entry.assetCode(), entry));
    }
    return List.copyOf(entriesByCode.values());
  }

  private Optional<StockPriceEntry> toStockEntry(
      PublicDataStockPriceItem item, LocalDate requestedDate) {
    if (item == null
        || isBlank(item.srtnCd())
        || !STOCK_CODE.matcher(item.srtnCd().trim()).matches()
        || isBlank(item.basDt())
        || isBlank(item.clpr())) {
      return Optional.empty();
    }

    try {
      LocalDate baseDate = LocalDate.parse(item.basDt().trim(), BASIC_DATE_FORMAT);
      BigDecimal closePrice = new BigDecimal(item.clpr().trim());
      if (!requestedDate.equals(baseDate) || closePrice.signum() <= 0) {
        return Optional.empty();
      }
      return Optional.of(
          new StockPriceEntry(item.srtnCd().trim(), AssetCategory.STOCK, baseDate, closePrice));
    } catch (DateTimeParseException | NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private List<StockPriceEntry> toEtfEntries(
      List<PublicDataEtfPriceItem> items, LocalDate requestedDate) {
    Map<String, StockPriceEntry> entriesByCode = new LinkedHashMap<>();
    for (PublicDataEtfPriceItem item : items) {
      toEtfEntry(item, requestedDate)
          .ifPresent(entry -> entriesByCode.put(entry.assetCode(), entry));
    }
    return List.copyOf(entriesByCode.values());
  }

  private Optional<StockPriceEntry> toEtfEntry(
      PublicDataEtfPriceItem item, LocalDate requestedDate) {
    if (item == null
        || isBlank(item.srtnCd())
        || !STOCK_CODE.matcher(item.srtnCd().trim()).matches()
        || isBlank(item.basDt())
        || isBlank(item.clpr())) {
      return Optional.empty();
    }

    try {
      LocalDate baseDate = LocalDate.parse(item.basDt().trim(), BASIC_DATE_FORMAT);
      BigDecimal closePrice = new BigDecimal(item.clpr().trim());
      if (!requestedDate.equals(baseDate) || closePrice.signum() <= 0) {
        return Optional.empty();
      }
      return Optional.of(
          new StockPriceEntry(item.srtnCd().trim(), AssetCategory.ETF, baseDate, closePrice));
    } catch (DateTimeParseException | NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }

  private record StockPriceSnapshot(LocalDate baseDate, List<StockPriceEntry> entries) {}

  private record StockPriceSelection(List<StockPriceSnapshot> snapshots, int failedDateCount) {}
}
