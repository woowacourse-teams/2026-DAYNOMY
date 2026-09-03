package org.grit.daynomy.search.repository;

import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface NewsSearchRepository extends Repository<News, Long> {

  // 부분 문자열 검색은 MVP용이며 데이터가 늘면 PostgreSQL 전문 검색으로 교체한다.
  @Query(
      """
      SELECT n
      FROM News n
      WHERE n.status = :status
        AND (:category IS NULL OR n.category = :category)
        AND (
          LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
          OR LOWER(n.description) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
          OR LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
        )
      """)
  Page<News> search(
      @Param("keyword") String keyword,
      @Param("category") Category category,
      @Param("status") NewsStatus status,
      Pageable pageable);
}
