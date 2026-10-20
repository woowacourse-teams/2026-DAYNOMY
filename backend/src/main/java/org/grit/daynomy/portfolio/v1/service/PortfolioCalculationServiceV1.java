package org.grit.daynomy.portfolio.v1.service;

import java.util.List;
import java.util.function.LongFunction;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.service.PortfolioCalculationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class PortfolioCalculationServiceV1 extends PortfolioCalculationService {
  private final StockDailyPriceRepository prices;

  public PortfolioCalculationServiceV1(
      StockDailyPriceRepository prices,
      AssetRepository assets,
      ExchangeRateService exchangeRates,
      PlatformTransactionManager transactionManager) {
    super(prices, assets, exchangeRates, transactionManager);
    this.prices = prices;
  }

  @Override
  protected LongFunction<List<StockDailyPrice>> priceLookup(
      List<PortfolioHoldingRequest> holdings) {
    return prices::findTop2ByAssetIdOrderByBaseDateDesc;
  }
}
