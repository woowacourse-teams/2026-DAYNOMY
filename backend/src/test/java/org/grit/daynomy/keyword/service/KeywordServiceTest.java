package org.grit.daynomy.keyword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDateTime;
import java.util.List;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.ErrorCode;
import org.grit.daynomy.keyword.entity.NewsKeywordEntity;
import org.grit.daynomy.keyword.repository.NewsKeywordRepository;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class KeywordServiceTest {

  @Mock private NewsRepository newsRepository;

  @Mock private NewsKeywordRepository newsKeywordRepository;

  @InjectMocks private KeywordService keywordService;

  @Test
  @DisplayName("뉴스 ID로 키워드 목록을 조회한다")
  void findKeywordsReturnsKeywords() {
    News news = createNews();
    given(newsRepository.existsById(1L)).willReturn(true);
    given(newsKeywordRepository.findByNewsIdOrderByIdAsc(1L))
        .willReturn(
            List.of(
                new NewsKeywordEntity(news, "금리 인하", "대출 수요 회복과 연결됨"),
                new NewsKeywordEntity(news, "부동산 규제", "거래량 회복 기대와 연결됨")));

    var response = keywordService.getKeywords(1L);

    assertThat(response.keywords()).hasSize(2);
    assertThat(response.keywords().get(0).keyword()).isEqualTo("금리 인하");
    assertThat(response.keywords().get(0).description()).isEqualTo("대출 수요 회복과 연결됨");
    verify(newsKeywordRepository).findByNewsIdOrderByIdAsc(1L);
  }

  @Test
  @DisplayName("뉴스가 없으면 키워드 조회 전에 예외를 던진다")
  void findKeywordsThrowsWhenNewsMissing() {
    given(newsRepository.existsById(1L)).willReturn(false);

    assertThatThrownBy(() -> keywordService.getKeywords(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ErrorCode.NEWS_NOT_FOUND);
    verify(newsKeywordRepository, never()).findByNewsIdOrderByIdAsc(1L);
  }

  private News createNews() {
    return new News(
        "keyword news",
        "content",
        "description",
        "image.png",
        Category.STOCK,
        LocalDateTime.of(2026, 8, 17, 10, 0));
  }
}
