package org.grit.daynomy.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.search.repository.NewsSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class NewsSearchServiceTest {

  @Mock private NewsSearchRepository newsSearchRepository;

  @InjectMocks private NewsSearchService newsSearchService;

  @Test
  @DisplayName("뉴스 검색은 검색어 공백을 제거하고 1-based 페이지를 Pageable로 변환한다")
  void searchNewsNormalizesKeywordAndPage() {
    PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
    given(newsSearchRepository.search("금리", Category.BOND, NewsStatus.PUBLISHED, pageable))
        .willReturn(Page.empty(pageable));

    var response = newsSearchService.search("  금리  ", Category.BOND, 1, 20);

    assertThat(response.page()).isEqualTo(1);
    then(newsSearchRepository).should().search("금리", Category.BOND, NewsStatus.PUBLISHED, pageable);
  }

  @Test
  @DisplayName("뉴스 검색은 LIKE 와일드카드와 escape 문자를 이스케이프한다")
  void searchNewsEscapesLikeWildcards() {
    PageRequest pageable = PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "publishedAt", "id"));
    given(newsSearchRepository.search("금!%리!_!!", null, NewsStatus.PUBLISHED, pageable))
        .willReturn(Page.empty(pageable));

    newsSearchService.search("금%리_!", null, 1, 20);

    then(newsSearchRepository).should().search("금!%리!_!!", null, NewsStatus.PUBLISHED, pageable);
  }
}
