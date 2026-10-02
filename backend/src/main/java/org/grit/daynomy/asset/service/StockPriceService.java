package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.StockPriceResponse;
import org.grit.daynomy.asset.dto.StockPricesResponse;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class StockPriceService {

  private final StockDailyPriceRepository stockDailyPriceRepository;

  @Transactional(readOnly = true)
  public StockPriceResponse getLatestPrice(Long assetId) {
    return stockDailyPriceRepository
        .findFirstByAssetIdOrderByBaseDateDesc(assetId)
        .map(StockPriceResponse::from)
        .orElseThrow(() -> new BusinessException(AssetErrorCode.STOCK_PRICE_NOT_FOUND));
  }

  @Transactional(readOnly = true)
  public StockPricesResponse getPrices(List<Long> assetIds, LocalDate from, LocalDate to) {
    if (from.isAfter(to) || ChronoUnit.DAYS.between(from, to) > 31) {
      throw new BusinessException(AssetErrorCode.INVALID_STOCK_PRICE_QUERY_RANGE);
    }
    List<Long> distinctAssetIds = assetIds.stream().distinct().toList();
    List<StockPriceResponse> prices =
        stockDailyPriceRepository
            .findAllByAssetIdInAndBaseDateBetweenOrderByBaseDateAscAssetIdAsc(
                distinctAssetIds, from, to)
            .stream()
            .map(StockPriceResponse::from)
            .toList();
    return new StockPricesResponse(prices);
  }
}
