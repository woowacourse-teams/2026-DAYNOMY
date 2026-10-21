package org.grit.daynomy.search.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.search.domain.ContentSearchSort;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.grit.daynomy.search.dto.ContentSearchResponse;
import org.grit.daynomy.search.repository.ContentSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContentSearchService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final ContentSearchRepository repository;
  private final Clock clock;

  @Transactional(readOnly = true)
  public ContentSearchResponse search(
      String keyword, ContentSearchSource source, int page, int size, ContentSearchSort sort) {
    return repository.search(
        NewsSearchTerms.from(keyword),
        source,
        page,
        size,
        sort,
        LocalDate.ofInstant(clock.instant(), SEOUL).minusDays(6));
  }
}
