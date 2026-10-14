package org.grit.daynomy.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.grit.daynomy.search.dto.ContentSearchResponse;
import org.grit.daynomy.search.repository.ContentSearchRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentSearchServiceTest {

  @Mock private ContentSearchRepository repository;
  @InjectMocks private ContentSearchService service;

  @Test
  void normalizesTermsAndPassesPageConditionsToRepository() {
    NewsSearchTerms terms = new NewsSearchTerms(List.of("금리", "etf"));
    ContentSearchResponse expected = new ContentSearchResponse(List.of(), 2, 12, 0, 0);
    given(repository.search(terms, ContentSearchSource.YOUTUBE, 2, 12)).willReturn(expected);

    assertThat(service.search(" 금리  ETF 금리 ", ContentSearchSource.YOUTUBE, 2, 12))
        .isEqualTo(expected);
    then(repository).should().search(terms, ContentSearchSource.YOUTUBE, 2, 12);
  }
}
