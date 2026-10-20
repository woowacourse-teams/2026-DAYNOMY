package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.eodhd.EodhdClient;
import org.grit.daynomy.external.eodhd.dto.EodhdDailyPrice;
import org.grit.daynomy.external.eodhd.dto.EodhdSymbol;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UsStockSyncService {
  private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");
  private static final Pattern TICKER = Pattern.compile("[A-Z0-9][A-Z0-9.-]{0,39}");
  private static final Set<AssetCategory> CATEGORIES =
      Set.of(AssetCategory.STOCK, AssetCategory.ETF);
  private final EodhdClient client;
  private final StockMasterPersistenceService masterPersistence;
  private final StockPricePersistenceService pricePersistence;
  private final AssetRepository assetRepository;
  private final AtomicBoolean running = new AtomicBoolean(false);

  public StockSyncResult synchronizeMaster() {
    acquire();
    try {
      LocalDate date = LocalDate.now(NEW_YORK);
      Map<String, StockMasterEntry> entries = new LinkedHashMap<>();
      for (EodhdSymbol symbol : client.getUsSymbols()) {
        AssetCategory category = category(symbol.type());
        var market = StockMarket.fromEodhd(symbol.exchange());
        // US also includes mutual funds, warrants and OTC listings; these are outside this feed.
        if (category == null || market.isEmpty()) continue;
        if (!"USD".equals(symbol.currency()) || blank(symbol.name()) || blank(symbol.code())) {
          throw new BusinessException(AssetErrorCode.US_STOCK_MASTER_DATA_NOT_FOUND);
        }
        String ticker = symbol.code().trim().toUpperCase(Locale.ROOT);
        if (!TICKER.matcher(ticker).matches()) {
          throw new BusinessException(AssetErrorCode.US_STOCK_MASTER_DATA_NOT_FOUND);
        }
        String isin = blank(symbol.isin()) ? null : symbol.isin().trim();
        if (isin != null && !isin.matches("[A-Z]{2}[A-Z0-9]{9}[0-9]")) isin = null;
        StockMasterEntry entry =
            new StockMasterEntry(
                ticker + ".US", symbol.name().trim(), category, market.get(), isin, date);
        if (entries.putIfAbsent(entry.code(), entry) != null) {
          throw new BusinessException(AssetErrorCode.US_STOCK_MASTER_DATA_NOT_FOUND);
        }
      }
      if (entries.values().stream().noneMatch(e -> e.category() == AssetCategory.STOCK)
          || entries.values().stream().noneMatch(e -> e.category() == AssetCategory.ETF)) {
        throw new BusinessException(AssetErrorCode.US_STOCK_MASTER_DATA_NOT_FOUND);
      }
      return masterPersistence.synchronizeUs(date, List.copyOf(entries.values()));
    } finally {
      running.set(false);
    }
  }

  public StockPriceSyncResult synchronizePrices(LocalDate date) {
    return synchronizePrices(date, LocalDate.now(NEW_YORK));
  }

  StockPriceSyncResult synchronizePrices(LocalDate requestedDate, LocalDate today) {
    if (requestedDate != null && !requestedDate.isBefore(today)) {
      throw new BusinessException(AssetErrorCode.INVALID_US_STOCK_PRICE_DATE);
    }
    acquire();
    try {
      Map<String, Asset> assets = new LinkedHashMap<>();
      for (Asset asset : assetRepository.findAllByCategoryIn(CATEGORIES)) {
        if (asset.isListed() && asset.getMarket() != null && asset.getMarket().isUs()) {
          assets.put(asset.getAssetCode(), asset);
        }
      }
      if (assets.isEmpty()) throw new BusinessException(AssetErrorCode.US_STOCK_MASTER_REQUIRED);
      LocalDate firstDate = requestedDate == null ? today.minusDays(1) : requestedDate;
      int attempts = requestedDate == null ? 10 : 1;
      for (int offset = 0; offset < attempts; offset++) {
        LocalDate date = firstDate.minusDays(offset);
        List<EodhdDailyPrice> prices = client.getUsDailyPrices(date);
        if (prices.isEmpty()) continue;
        List<StockPriceEntry> entries = toPrices(prices, assets, date);
        if (entries.isEmpty())
          throw new BusinessException(AssetErrorCode.US_STOCK_PRICE_DATA_NOT_FOUND);
        return pricePersistence.synchronizeUs(date, entries);
      }
      throw new BusinessException(AssetErrorCode.US_STOCK_PRICE_DATA_NOT_FOUND);
    } finally {
      running.set(false);
    }
  }

  private List<StockPriceEntry> toPrices(
      List<EodhdDailyPrice> prices, Map<String, Asset> assets, LocalDate date) {
    Map<String, StockPriceEntry> entries = new LinkedHashMap<>();
    for (EodhdDailyPrice price : prices) {
      if (blank(price.code())) continue;
      String code = price.code().trim().toUpperCase(Locale.ROOT) + ".US";
      Asset asset = assets.get(code);
      if (asset == null) continue;
      // Do not silently store another session or substitute adjusted_close for the actual close.
      if (!date.equals(price.date())) {
        throw new BusinessException(AssetErrorCode.US_STOCK_PRICE_DATA_NOT_FOUND);
      }
      if (price.close() == null || price.close().signum() <= 0) continue;
      StockPriceEntry entry = new StockPriceEntry(code, asset.getCategory(), date, price.close());
      if (entries.putIfAbsent(code, entry) != null) {
        throw new BusinessException(AssetErrorCode.US_STOCK_PRICE_DATA_NOT_FOUND);
      }
    }
    return new ArrayList<>(entries.values());
  }

  private AssetCategory category(String type) {
    if ("Common Stock".equals(type)) return AssetCategory.STOCK;
    if ("ETF".equals(type)) return AssetCategory.ETF;
    return null;
  }

  private boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private void acquire() {
    if (!running.compareAndSet(false, true)) {
      throw new BusinessException(AssetErrorCode.US_STOCK_SYNC_ALREADY_RUNNING);
    }
  }
}
