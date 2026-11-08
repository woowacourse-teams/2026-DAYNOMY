package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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
  private NewsFeedService service;

  @BeforeEach
  void setUp() {
    service = new NewsFeedService(repository, clock);
  }

  @Test
  void requestsPopularFeedForTheLastSevenKoreanDates() {
    LocalDate fromDate = LocalDate.of(2026, 10, 15);
    given(clock.instant()).willReturn(Instant.parse("2026-10-20T15:00:00Z"));
    given(
            repository.findFeed(
                ContentSearchSource.DAYNOMY,
                2,
                4,
                ContentSearchSort.POPULAR,
                fromDate))
        .willReturn(new ContentSearchResponse(List.of(), 2, 4, 10, 3));

    assertThat(service.getFeed(NewsFeedSource.DAYNOMY, 2, 4, NewsFeedSort.POPULAR))
        .isEqualTo(new NewsFeedResponse(List.of(), 2, 4, 10, 3));
    then(repository)
        .should()
        .findFeed(
            ContentSearchSource.DAYNOMY,
            2,
            4,
            ContentSearchSort.POPULAR,
            fromDate);
  }
}
