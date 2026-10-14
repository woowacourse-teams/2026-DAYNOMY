package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.dto.StockPriceResponse;
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
  public Map<Long, StockDailyPrice> pricesAt(List<Long> assetIds, LocalDate date) {
    if (date == null || assetIds.isEmpty()) return Map.of();
    return stockDailyPriceRepository.findAllByAssetIdInAndBaseDate(assetIds, date).stream()
        .collect(Collectors.toMap(price -> price.getAsset().getId(), Function.identity()));
  }

  @Transactional(readOnly = true)
  public StockPriceResponse getLatestPrice(Long assetId) {
    return stockDailyPriceRepository
        .findFirstByAssetIdOrderByBaseDateDesc(assetId)
        .map(StockPriceResponse::from)
        .orElseThrow(() -> new BusinessException(AssetErrorCode.STOCK_PRICE_NOT_FOUND));
  }
}
