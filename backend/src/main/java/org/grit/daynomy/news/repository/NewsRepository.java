package org.grit.daynomy.news.repository;

import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NewsRepository extends JpaRepository<News, Long> {

  Page<News> findAllByOrderByPublishedAtDescIdDesc(Pageable pageable);

  Page<News> findByCategoryOrderByPublishedAtDescIdDesc(Category category, Pageable pageable);
}
