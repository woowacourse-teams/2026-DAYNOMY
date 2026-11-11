package org.grit.daynomy.news.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.service.IndustryService;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.domain.NewsFeedSort;
import org.grit.daynomy.news.domain.NewsFeedSource;
import org.grit.daynomy.news.dto.NewsFeedResponse;
import org.grit.daynomy.search.domain.ContentSearchSort;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.repository.ContentSearchRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NewsFeedService {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

  private final ContentSearchRepository repository;
  private final Clock clock;
  private final IndustryService industryService;

  @Transactional(readOnly = true)
  public NewsFeedResponse getFeed(
      NewsFeedSource source, int page, int size, NewsFeedSort sort, String industry) {
    if (industry != null && !industryService.isActive(industry)) {
      throw new BusinessException(AssetErrorCode.INDUSTRY_NOT_FOUND);
    }
    return NewsFeedResponse.from(
        repository.findFeed(
            ContentSearchSource.valueOf(source.name()),
            page,
            size,
            ContentSearchSort.valueOf(sort.name()),
            LocalDate.ofInstant(clock.instant(), SEOUL).minusDays(6),
            industry));
  }
}
