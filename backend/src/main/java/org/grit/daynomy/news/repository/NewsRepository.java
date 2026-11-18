package org.grit.daynomy.news.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NewsRepository extends JpaRepository<News, Long> {

  boolean existsByIdAndStatus(Long id, NewsStatus status);

  Page<News> findByStatusOrderByPublishedAtDescIdDesc(NewsStatus status, Pageable pageable);

  Page<News> findByStatusAndCategoryOrderByPublishedAtDescIdDesc(
      NewsStatus status, Category category, Pageable pageable);

  Optional<News> findByIdAndStatus(Long id, NewsStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select news from News news where news.id = :id")
  Optional<News> findByIdForUpdate(@Param("id") Long id);

  Page<News> findByStatus(NewsStatus status, Pageable pageable);

  Page<News> findByCategory(Category category, Pageable pageable);

  Page<News> findByStatusAndCategory(NewsStatus status, Category category, Pageable pageable);
}
