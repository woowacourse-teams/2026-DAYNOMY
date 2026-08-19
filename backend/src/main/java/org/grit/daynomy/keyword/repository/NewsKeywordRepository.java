package org.grit.daynomy.keyword.repository;

import java.util.List;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsKeywordRepository extends JpaRepository<NewsKeyword, Long> {

  List<NewsKeyword> findByNewsIdOrderByIdAsc(Long newsId);
}
