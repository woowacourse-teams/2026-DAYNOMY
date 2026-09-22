package org.grit.daynomy.asset.service;

import lombok.RequiredArgsConstructor;
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
  public StockPriceResponse getLatestPrice(Long assetId) {
    return stockDailyPriceRepository
        .findFirstByAssetIdOrderByBaseDateDesc(assetId)
        .map(StockPriceResponse::from)
        .orElseThrow(() -> new BusinessException(AssetErrorCode.STOCK_PRICE_NOT_FOUND));
  }
}
