package org.grit.daynomy.market.repository;

import java.util.Optional;
import org.grit.daynomy.market.entity.NewsMarketAnalysisEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsMarketAnalysisRepository
    extends JpaRepository<NewsMarketAnalysisEntity, Long> {

  Optional<NewsMarketAnalysisEntity> findByNewsId(Long newsId);
}
