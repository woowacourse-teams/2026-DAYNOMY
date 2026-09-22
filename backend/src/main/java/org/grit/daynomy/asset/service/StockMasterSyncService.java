package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.publicdata.PublicDataListedStockClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataListedStockItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataListedStockResponse;
import org.springframework.stereotype.Service;

@Service
public class StockMasterSyncService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private static final int PAGE_SIZE = 1000;
  private static final int LOOKBACK_DAYS = 10;
  private static final Pattern STOCK_CODE = Pattern.compile("(?:A)?\\d{6}");
  private static final Pattern PREFERRED_STOCK_NAME = Pattern.compile(".*(?:\\d+)?우(?:[A-Z])?$");

  private final PublicDataListedStockClient listedStockClient;
  private final StockMasterPersistenceService persistenceService;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public StockMasterSyncService(
      PublicDataListedStockClient listedStockClient,
      StockMasterPersistenceService persistenceService) {
    this.listedStockClient = listedStockClient;
    this.persistenceService = persistenceService;
  }

  public StockSyncResult synchronize() {
    return synchronize(LocalDate.now(SEOUL));
  }

  StockSyncResult synchronize(LocalDate today) {
    if (!running.compareAndSet(false, true)) {
      throw new BusinessException(AssetErrorCode.STOCK_SYNC_ALREADY_RUNNING);
    }

    try {
      StockMasterSnapshot snapshot = findLatestSnapshot(today);
      return persistenceService.synchronize(snapshot.baseDate(), snapshot.entries());
    } finally {
      running.set(false);
    }
  }

  private StockMasterSnapshot findLatestSnapshot(LocalDate today) {
    for (int daysAgo = 0; daysAgo <= LOOKBACK_DAYS; daysAgo++) {
      LocalDate requestedDate = today.minusDays(daysAgo);
      PublicDataListedStockResponse firstPage =
          listedStockClient.getListedStocks(requestedDate, 1, PAGE_SIZE);
      if (firstPage.body().totalCount() == 0 || firstPage.items().isEmpty()) {
        continue;
      }

      List<PublicDataListedStockItem> items = new ArrayList<>(firstPage.items());
      int totalPages = (firstPage.body().totalCount() + PAGE_SIZE - 1) / PAGE_SIZE;
      for (int page = 2; page <= totalPages; page++) {
        PublicDataListedStockResponse nextPage =
            listedStockClient.getListedStocks(requestedDate, page, PAGE_SIZE);
        if (nextPage.items().isEmpty()) {
          throw new BusinessException(AssetErrorCode.STOCK_MASTER_DATA_NOT_FOUND);
        }
        items.addAll(nextPage.items());
      }
      if (items.size() < firstPage.body().totalCount()) {
        throw new BusinessException(AssetErrorCode.STOCK_MASTER_DATA_NOT_FOUND);
      }

      List<StockMasterEntry> entries = toEntries(items, requestedDate);
      if (!entries.isEmpty()) {
        return new StockMasterSnapshot(entries.getFirst().baseDate(), entries);
      }
    }

    throw new BusinessException(AssetErrorCode.STOCK_MASTER_DATA_NOT_FOUND);
  }

  private List<StockMasterEntry> toEntries(
      List<PublicDataListedStockItem> items, LocalDate requestedDate) {
    Map<String, StockMasterEntry> entriesByCode = new LinkedHashMap<>();
    for (PublicDataListedStockItem item : items) {
      toEntry(item, requestedDate).ifPresent(entry -> entriesByCode.put(entry.code(), entry));
    }
    return List.copyOf(entriesByCode.values());
  }

  private Optional<StockMasterEntry> toEntry(
      PublicDataListedStockItem item, LocalDate requestedDate) {
    if (item == null
        || isBlank(item.srtnCd())
        || !STOCK_CODE.matcher(item.srtnCd().trim().toUpperCase(Locale.ROOT)).matches()
        || isBlank(item.itmsNm())
        || isBlank(item.isinCd())
        || isBlank(item.basDt())) {
      return Optional.empty();
    }

    String name = item.itmsNm().trim();
    if (isExcludedStock(name)) {
      return Optional.empty();
    }

    try {
      LocalDate baseDate = LocalDate.parse(item.basDt().trim(), BASIC_DATE_FORMAT);
      if (!requestedDate.equals(baseDate)) {
        return Optional.empty();
      }
      String stockCode = item.srtnCd().trim().toUpperCase(Locale.ROOT).replaceFirst("^A", "");
      return StockMarket.from(normalizeMarket(item.mrktCtg()))
          .map(
              market ->
                  new StockMasterEntry(stockCode, name, market, item.isinCd().trim(), baseDate));
    } catch (DateTimeParseException exception) {
      return Optional.empty();
    }
  }

  private boolean isExcludedStock(String name) {
    String normalizedName = name.toUpperCase(Locale.ROOT);
    return normalizedName.contains("스팩")
        || normalizedName.contains("SPAC")
        || normalizedName.contains("우선주")
        || PREFERRED_STOCK_NAME.matcher(normalizedName).matches();
  }

  private String normalizeMarket(String market) {
    return market == null ? "" : market.trim().toUpperCase(Locale.ROOT);
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record StockMasterSnapshot(LocalDate baseDate, List<StockMasterEntry> entries) {}
}
