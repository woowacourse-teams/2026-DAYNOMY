package org.grit.daynomy.portfolio.repository;

import org.grit.daynomy.portfolio.domain.PortfolioTransactionImport;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioTransactionImportRepository
    extends JpaRepository<PortfolioTransactionImport, Long> {
  boolean existsByPortfolioIdAndFingerprint(Long portfolioId, String fingerprint);
}
