package org.grit.daynomy.search.repository;

import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface NewsSearchRepository
    extends Repository<News, Long>, JpaSpecificationExecutor<News> {

  // 관리자 검색은 검색어 전체에 대한 부분 일치와 기존 상태·정렬 조건을 유지한다.
  @Query(
      """
      SELECT n
      FROM News n
      WHERE (:status IS NULL OR n.status = :status)
        AND (:category IS NULL OR n.category = :category)
        AND (
          LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
          OR LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '!'
        )
      """)
  Page<News> search(
      @Param("keyword") String keyword,
      @Param("category") Category category,
      @Param("status") NewsStatus status,
      Pageable pageable);
}
