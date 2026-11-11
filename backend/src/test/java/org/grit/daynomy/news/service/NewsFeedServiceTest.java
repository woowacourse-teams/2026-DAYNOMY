package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.asset.service.IndustryService;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.domain.NewsFeedSort;
import org.grit.daynomy.news.domain.NewsFeedSource;
import org.grit.daynomy.news.dto.NewsFeedResponse;
import org.grit.daynomy.search.domain.ContentSearchSort;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.dto.ContentSearchResponse;
import org.grit.daynomy.search.repository.ContentSearchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsFeedServiceTest {

  @Mock private ContentSearchRepository repository;
  @Mock private Clock clock;
  @Mock private IndustryService industryService;
  private NewsFeedService service;

  @BeforeEach
  void setUp() {
    service = new NewsFeedService(repository, clock, industryService);
  }

  @Test
  void requestsPopularFeedForTheLastSevenKoreanDates() {
    LocalDate fromDate = LocalDate.of(2026, 10, 15);
    given(clock.instant()).willReturn(Instant.parse("2026-10-20T15:00:00Z"));
    given(
            repository.findFeed(
                ContentSearchSource.DAYNOMY, 2, 4, ContentSearchSort.POPULAR, fromDate, null))
        .willReturn(new ContentSearchResponse(List.of(), 2, 4, 10, 3));

    assertThat(service.getFeed(NewsFeedSource.DAYNOMY, 2, 4, NewsFeedSort.POPULAR, null))
        .isEqualTo(new NewsFeedResponse(List.of(), 2, 4, 10, 3));
    then(repository)
        .should()
        .findFeed(ContentSearchSource.DAYNOMY, 2, 4, ContentSearchSort.POPULAR, fromDate, null);
  }

  @Test
  void filtersFeedByActiveIndustry() {
    LocalDate fromDate = LocalDate.of(2026, 10, 15);
    given(clock.instant()).willReturn(Instant.parse("2026-10-20T15:00:00Z"));
    given(industryService.isActive("SEMICONDUCTOR")).willReturn(true);
    given(
            repository.findFeed(
                ContentSearchSource.ALL, 1, 8, ContentSearchSort.LATEST, fromDate, "SEMICONDUCTOR"))
        .willReturn(new ContentSearchResponse(List.of(), 1, 8, 0, 0));

    assertThat(service.getFeed(NewsFeedSource.ALL, 1, 8, NewsFeedSort.LATEST, "SEMICONDUCTOR"))
        .isEqualTo(new NewsFeedResponse(List.of(), 1, 8, 0, 0));
  }

  @Test
  void rejectsInactiveIndustry() {
    given(industryService.isActive("UNKNOWN")).willReturn(false);

    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> service.getFeed(NewsFeedSource.ALL, 1, 8, NewsFeedSort.LATEST, "UNKNOWN"))
        .isInstanceOf(BusinessException.class);
  }
}
