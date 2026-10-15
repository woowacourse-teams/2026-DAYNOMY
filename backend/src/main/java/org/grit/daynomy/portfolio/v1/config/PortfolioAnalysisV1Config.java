package org.grit.daynomy.portfolio.v1.config;

import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCheckRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisEvidenceRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisTransactionService;
import org.grit.daynomy.portfolio.v1.service.PortfolioAnalysisServiceV1;
import org.grit.daynomy.portfolio.v1.service.PortfolioCalculationServiceV1;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PortfolioAnalysisV1Config {
  @Bean("portfolioAnalysisTransactionsV1")
  SavedPortfolioAnalysisTransactionService transactions(
      PortfolioRepository portfolios,
      PortfolioHoldingRepository holdings,
      PortfolioAnalysisRepository analyses,
      PortfolioAnalysisEvidenceRepository evidence,
      PortfolioAnalysisCheckRepository checks,
      PortfolioCalculationServiceV1 calculation,
      @Value("${ai.openai.model:gpt-5-mini}") String model) {
    return new SavedPortfolioAnalysisTransactionService(
        portfolios,
        holdings,
        analyses,
        evidence,
        checks,
        calculation,
        model,
        "portfolio-v5-price-v1");
  }

  @Bean("savedPortfolioAnalysisV1")
  SavedPortfolioAnalysisService analysis(
      @Qualifier("portfolioAnalysisTransactionsV1")
          SavedPortfolioAnalysisTransactionService transactions,
      PortfolioAnalysisServiceV1 analysisService) {
    return new SavedPortfolioAnalysisService(transactions, analysisService);
  }
}
