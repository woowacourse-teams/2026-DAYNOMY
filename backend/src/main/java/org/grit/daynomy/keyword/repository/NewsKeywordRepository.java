package org.grit.daynomy.keyword.repository;

import java.util.List;
import org.grit.daynomy.keyword.domain.entity.NewsKeywordEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsKeywordRepository extends JpaRepository<NewsKeywordEntity, Long> {

  List<NewsKeywordEntity> findByNewsIdOrderByIdAsc(Long newsId);
}
