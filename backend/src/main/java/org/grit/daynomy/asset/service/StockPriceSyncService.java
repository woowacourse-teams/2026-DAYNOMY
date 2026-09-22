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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.publicdata.PublicDataStockPriceClient;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.springframework.stereotype.Service;

@Service
public class StockPriceSyncService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private static final int PAGE_SIZE = 1000;
  private static final int LOOKBACK_DAYS = 10;
  private static final Pattern STOCK_CODE = Pattern.compile("\\d{6}");

  private final PublicDataStockPriceClient stockPriceClient;
  private final StockPricePersistenceService persistenceService;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public StockPriceSyncService(
      PublicDataStockPriceClient stockPriceClient,
      StockPricePersistenceService persistenceService) {
    this.stockPriceClient = stockPriceClient;
    this.persistenceService = persistenceService;
  }

  public StockPriceSyncResult synchronize() {
    return synchronize(LocalDate.now(SEOUL));
  }

  StockPriceSyncResult synchronize(LocalDate today) {
    if (!running.compareAndSet(false, true)) {
      throw new BusinessException(AssetErrorCode.STOCK_PRICE_SYNC_ALREADY_RUNNING);
    }

    try {
      StockPriceSnapshot snapshot = findLatestSnapshot(today);
      return persistenceService.synchronize(snapshot.baseDate(), snapshot.entries());
    } finally {
      running.set(false);
    }
  }

  private StockPriceSnapshot findLatestSnapshot(LocalDate today) {
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

      List<StockPriceEntry> entries = toEntries(items, requestedDate);
      if (!entries.isEmpty()) {
        return new StockPriceSnapshot(requestedDate, entries);
      }
    }

    throw new BusinessException(AssetErrorCode.STOCK_PRICE_DATA_NOT_FOUND);
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
    return items;
  }

  private List<StockPriceEntry> toEntries(
      List<PublicDataStockPriceItem> items, LocalDate requestedDate) {
    Map<String, StockPriceEntry> entriesByCode = new LinkedHashMap<>();
    for (PublicDataStockPriceItem item : items) {
      toEntry(item, requestedDate).ifPresent(entry -> entriesByCode.put(entry.assetCode(), entry));
    }
    return List.copyOf(entriesByCode.values());
  }

  private Optional<StockPriceEntry> toEntry(
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
      return Optional.of(new StockPriceEntry(item.srtnCd().trim(), baseDate, closePrice));
    } catch (DateTimeParseException | NumberFormatException exception) {
      return Optional.empty();
    }
  }

  private boolean isBlank(String value) {
    return value == null || value.isBlank();
  }

  private record StockPriceSnapshot(LocalDate baseDate, List<StockPriceEntry> entries) {}
}
