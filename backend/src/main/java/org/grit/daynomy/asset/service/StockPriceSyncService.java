package org.grit.daynomy.asset.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.publicdata.PublicDataEtfPriceClient;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataEtfPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataEtfPriceResponse;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class StockPriceSyncService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private static final int PAGE_SIZE = 1000;
  private static final int LOOKBACK_DAYS = 10;
  private static final int REQUIRED_SNAPSHOT_COUNT = 2;
  private static final Pattern STOCK_CODE = Pattern.compile("\\d{6}");

  private final PublicDataStockPriceClient stockPriceClient;
  private final PublicDataEtfPriceClient etfPriceClient;
  private final StockPricePersistenceService persistenceService;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public StockPriceSyncService(
      PublicDataStockPriceClient stockPriceClient,
      PublicDataEtfPriceClient etfPriceClient,
      StockPricePersistenceService persistenceService) {
    this.stockPriceClient = stockPriceClient;
    this.etfPriceClient = etfPriceClient;
    this.persistenceService = persistenceService;
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
      List<StockPriceSnapshot> snapshots = findLatestSnapshots(today);
      for (int index = snapshots.size() - 1; index > 0; index--) {
        StockPriceSnapshot snapshot = snapshots.get(index);
        persistenceService.synchronize(snapshot.baseDate(), snapshot.entries());
      }
      StockPriceSnapshot latestSnapshot = snapshots.getFirst();
      StockPriceSyncResult result =
          persistenceService.synchronize(latestSnapshot.baseDate(), latestSnapshot.entries());
      log.atInfo()
          .addKeyValue("event", LogEvent.STOCK_PRICE_SYNC_COMPLETED.code())
          .addKeyValue("baseDate", result.baseDate())
          .addKeyValue("receivedCount", result.receivedCount())
          .addKeyValue("createdCount", result.createdCount())
          .addKeyValue("updatedCount", result.updatedCount())
          .addKeyValue("skippedCount", result.skippedCount())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.STOCK_PRICE_SYNC_COMPLETED.message());
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

  private List<StockPriceSnapshot> findLatestSnapshots(LocalDate today) {
    List<StockPriceSnapshot> snapshots = new ArrayList<>();
    for (int daysAgo = 0; daysAgo <= LOOKBACK_DAYS; daysAgo++) {
      LocalDate requestedDate = today.minusDays(daysAgo);
      List<PublicDataStockPriceItem> items = new ArrayList<>();
      boolean complete = true;

      for (StockMarket market : StockMarket.values()) {
        List<PublicDataStockPriceItem> marketItems = getAllPages(requestedDate, market);
        if (marketItems.isEmpty()) {
          complete = false;
          break;
        }
        items.addAll(marketItems);
      }

      if (!complete) {
        continue;
      }

      List<PublicDataEtfPriceItem> etfItems = getAllEtfPages(requestedDate);
      List<StockPriceEntry> entries = new ArrayList<>(toStockEntries(items, requestedDate));
      entries.addAll(toEtfEntries(etfItems, requestedDate));
      if (!entries.isEmpty()) {
        snapshots.add(new StockPriceSnapshot(requestedDate, List.copyOf(entries)));
        if (snapshots.size() == REQUIRED_SNAPSHOT_COUNT) {
          return List.copyOf(snapshots);
        }
      }
    }

    if (snapshots.isEmpty()) {
      throw new BusinessException(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
    }
    return List.copyOf(snapshots);
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
}
