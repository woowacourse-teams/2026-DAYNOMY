package org.grit.daynomy.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.search.domain.ContentSearchSort;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.grit.daynomy.search.dto.ContentSearchResponse;
import org.grit.daynomy.search.repository.ContentSearchRepository;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentSearchServiceTest {

  @Mock private ContentSearchRepository repository;
  @Mock private Clock clock;
  @InjectMocks private ContentSearchService service;

  @ParameterizedTest
  @EnumSource(ContentSearchSort.class)
  void normalizesTermsAndUsesSevenSeoulDates(ContentSearchSort sort) {
    NewsSearchTerms terms = new NewsSearchTerms(List.of("금리", "etf"));
    ContentSearchResponse expected = new ContentSearchResponse(List.of(), 2, 12, 0, 0);
    given(clock.instant()).willReturn(Instant.parse("2026-10-20T15:00:00Z"));
    LocalDate fromDate = LocalDate.of(2026, 10, 15);
    given(repository.search(terms, ContentSearchSource.YOUTUBE, 2, 12, sort, fromDate))
        .willReturn(expected);

    assertThat(service.search(" 금리  ETF 금리 ", ContentSearchSource.YOUTUBE, 2, 12, sort))
        .isEqualTo(expected);
    then(repository).should().search(terms, ContentSearchSource.YOUTUBE, 2, 12, sort, fromDate);
  }
}
