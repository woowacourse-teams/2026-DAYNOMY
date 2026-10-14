package org.grit.daynomy.search.repository;

import org.grit.daynomy.news.domain.News;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.repository.Repository;

public interface NewsSearchRepository
    extends Repository<News, Long>, JpaSpecificationExecutor<News> {}
