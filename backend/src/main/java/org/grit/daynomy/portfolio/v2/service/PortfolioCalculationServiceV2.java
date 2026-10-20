package org.grit.daynomy.portfolio.v2.service;

import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.portfolio.service.PortfolioCalculationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;

@Service
public class PortfolioCalculationServiceV2 extends PortfolioCalculationService {
  public PortfolioCalculationServiceV2(
      StockDailyPriceRepository prices,
      AssetRepository assets,
      ExchangeRateService exchangeRates,
      PlatformTransactionManager transactionManager) {
    super(prices, assets, exchangeRates, transactionManager);
  }
}
