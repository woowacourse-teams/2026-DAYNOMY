package org.grit.daynomy.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.search.domain.NewsSearchSort;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.grit.daynomy.search.repository.NewsSearchRepository;
import org.grit.daynomy.search.repository.NewsSearchSpecification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class NewsSearchServiceTest {

  @Mock private NewsSearchRepository newsSearchRepository;

  @InjectMocks private NewsSearchService newsSearchService;

  @Test
  @DisplayName("뉴스 검색은 공백·중복 단어·대소문자를 정규화하고 1-based 페이지를 변환한다")
  void searchNewsNormalizesKeywordAndPage() {
    PageRequest pageable = PageRequest.of(0, 20);
    var specification =
        new NewsSearchSpecification(
            new NewsSearchTerms(List.of("금리", "인하", "etf")), Category.ETF, NewsSearchSort.LATEST);
    given(newsSearchRepository.findAll(specification, pageable)).willReturn(Page.empty(pageable));

    var response =
        newsSearchService.search(
            "  금리\t인하\u2003ETF etf 금리  ", Category.ETF, 1, 20, NewsSearchSort.LATEST);

    assertThat(response.page()).isEqualTo(1);
    then(newsSearchRepository).should().findAll(specification, pageable);
  }

  @Test
  @DisplayName("검색 특수문자는 단어의 일부로 유지하고 관련도 정렬과 다음 페이지를 전달한다")
  void searchNewsPreservesLiteralTermsAndRelevance() {
    PageRequest pageable = PageRequest.of(1, 20);
    var specification =
        new NewsSearchSpecification(
            new NewsSearchTerms(List.of("금%리_!")), null, NewsSearchSort.RELEVANCE);
    given(newsSearchRepository.findAll(specification, pageable)).willReturn(Page.empty(pageable));

    var response = newsSearchService.search("금%리_!", null, 2, 20, NewsSearchSort.RELEVANCE);

    assertThat(response.page()).isEqualTo(2);
    then(newsSearchRepository).should().findAll(specification, pageable);
  }
}
