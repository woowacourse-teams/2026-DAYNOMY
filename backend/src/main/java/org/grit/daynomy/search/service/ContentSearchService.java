package org.grit.daynomy.search.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.grit.daynomy.search.dto.ContentSearchResponse;
import org.grit.daynomy.search.repository.ContentSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentSearchService {

  private final ContentSearchRepository repository;

  @Transactional(readOnly = true)
  public ContentSearchResponse search(
      String keyword, ContentSearchSource source, int page, int size) {
    return repository.search(NewsSearchTerms.from(keyword), source, page, size);
  }
}
