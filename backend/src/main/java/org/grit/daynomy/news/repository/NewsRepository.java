package org.grit.daynomy.news.repository;

import java.time.LocalDateTime;
import java.util.Optional;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsRepository extends JpaRepository<News, Long> {

  Page<News> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);

  Page<News> findByCategoryOrderByPublishedAtDescIdDesc(Category category, Pageable pageable);

  // 부분 문자열 검색은 MVP용이며 데이터가 늘면 PostgreSQL 전문 검색으로 교체한다.
  @Query(
      """
      SELECT n
      FROM News n
      WHERE (:category IS NULL OR n.category = :category)
        AND (
          LOWER(n.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(n.description) LIKE LOWER(CONCAT('%', :keyword, '%'))
          OR LOWER(n.content) LIKE LOWER(CONCAT('%', :keyword, '%'))
        )
      """)
  Page<News> search(
      @Param("keyword") String keyword, @Param("category") Category category, Pageable pageable);

  Optional<News>
      findFirstByPublishedAtGreaterThanEqualAndPublishedAtLessThanOrderByPublishedAtDescIdDesc(
          LocalDateTime startInclusive, LocalDateTime endExclusive);
}
