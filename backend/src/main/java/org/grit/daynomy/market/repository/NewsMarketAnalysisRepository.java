package org.grit.daynomy.market.repository;

import java.util.Optional;
import org.grit.daynomy.market.domain.analysis.NewsMarketAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsMarketAnalysisRepository
    extends JpaRepository<NewsMarketAnalysis, Long> {

  Optional<NewsMarketAnalysis> findByNewsId(Long newsId);
}
