package org.grit.daynomy.portfolio.v2.config;

import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCheckRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisEvidenceRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisTransactionService;
import org.grit.daynomy.portfolio.v2.service.PortfolioAnalysisServiceV2;
import org.grit.daynomy.portfolio.v2.service.PortfolioCalculationServiceV2;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class PortfolioAnalysisV2Config {
  @Bean("portfolioAnalysisTransactionsV2")
  SavedPortfolioAnalysisTransactionService transactions(
      PortfolioRepository portfolios,
      PortfolioHoldingRepository holdings,
      PortfolioAnalysisRepository analyses,
      PortfolioAnalysisEvidenceRepository evidence,
      PortfolioAnalysisCheckRepository checks,
      PortfolioCalculationServiceV2 calculation,
      PlatformTransactionManager transactionManager,
      @Value("${ai.openai.model:gpt-5-mini}") String model) {
    return new SavedPortfolioAnalysisTransactionService(
        portfolios,
        holdings,
        analyses,
        evidence,
        checks,
        calculation,
        transactionManager,
        model,
        "portfolio-v8-select5-price-ai-v2");
  }

  @Bean("savedPortfolioAnalysisV2")
  SavedPortfolioAnalysisService analysis(
      @Qualifier("portfolioAnalysisTransactionsV2")
          SavedPortfolioAnalysisTransactionService transactions,
      PortfolioAnalysisServiceV2 analysisService) {
    return new SavedPortfolioAnalysisService(transactions, analysisService);
  }
}
