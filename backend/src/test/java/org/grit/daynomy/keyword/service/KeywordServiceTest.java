package org.grit.daynomy.keyword.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.keyword.exception.KeywordErrorCode;
import org.grit.daynomy.keyword.repository.NewsKeywordRepository;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
import org.grit.daynomy.news.exception.NewsErrorCode;
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
  @DisplayName("뉴스와 키워드 목록을 받아 키워드를 저장한다")
  void saveKeywordsStoresNewsKeywords() {
    News news = createNews();
    List<NewsKeyword> keywords = List.of(createKeyword("금리 인하"), createKeyword("부동산 규제"));

    keywordService.saveKeywords(news, keywords);

    verify(newsKeywordRepository)
        .saveAll(
            argThat(
                entities -> {
                  List<NewsKeyword> savedKeywords = new ArrayList<>();
                  entities.forEach(savedKeywords::add);

                  assertThat(savedKeywords).hasSize(2);
                  assertThat(savedKeywords.get(0).getNews()).isSameAs(news);
                  assertThat(savedKeywords.get(0).getCategory()).isEqualTo(KeywordCategory.POLICY);
                  assertThat(savedKeywords.get(0).getKeyword()).isEqualTo("금리 인하");
                  assertThat(savedKeywords.get(0).getPoint1()).isEqualTo("첫 번째 분석 포인트");
                  assertThat(savedKeywords.get(0).getPoint2()).isEqualTo("두 번째 분석 포인트");
                  assertThat(savedKeywords.get(0).getPoint3()).isEqualTo("세 번째 분석 포인트");
                  assertThat(savedKeywords.get(1).getKeyword()).isEqualTo("부동산 규제");
                  return true;
                }));
  }

  @Test
  @DisplayName("뉴스 ID로 키워드 목록을 조회한다")
  void findKeywordsReturnsKeywords() {
    News news = createNews();
    given(newsRepository.existsById(1L)).willReturn(true);
    given(newsKeywordRepository.findByNewsIdOrderByIdAsc(1L))
        .willReturn(List.of(createSavedKeyword(news, "금리 인하"), createSavedKeyword(news, "부동산 규제")));

    var response = keywordService.getKeywords(1L);

    assertThat(response.keywords()).hasSize(2);
    assertThat(response.keywords().get(0).category()).isEqualTo(KeywordCategory.POLICY);
    assertThat(response.keywords().get(0).keyword()).isEqualTo("금리 인하");
    assertThat(response.keywords().get(0).points())
        .containsExactly("첫 번째 분석 포인트", "두 번째 분석 포인트", "세 번째 분석 포인트");
    verify(newsKeywordRepository).findByNewsIdOrderByIdAsc(1L);
  }

  @Test
  @DisplayName("뉴스가 없으면 키워드 조회 전에 예외를 던진다")
  void findKeywordsThrowsWhenNewsMissing() {
    given(newsRepository.existsById(1L)).willReturn(false);

    assertThatThrownBy(() -> keywordService.getKeywords(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(NewsErrorCode.NEWS_NOT_FOUND);
    verify(newsKeywordRepository, never()).findByNewsIdOrderByIdAsc(1L);
  }

  @Test
  @DisplayName("키워드가 없으면 도메인 예외를 던진다")
  void findKeywordsThrowsWhenKeywordsMissing() {
    given(newsRepository.existsById(1L)).willReturn(true);
    given(newsKeywordRepository.findByNewsIdOrderByIdAsc(1L)).willReturn(List.of());

    assertThatThrownBy(() -> keywordService.getKeywords(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(KeywordErrorCode.KEYWORD_NOT_FOUND);
  }

  private NewsKeyword createKeyword(String keyword) {
    return new NewsKeyword(
        KeywordCategory.POLICY, keyword, "첫 번째 분석 포인트", "두 번째 분석 포인트", "세 번째 분석 포인트");
  }

  private NewsKeyword createSavedKeyword(News news, String keyword) {
    return new NewsKeyword(
        news, KeywordCategory.POLICY, keyword, "첫 번째 분석 포인트", "두 번째 분석 포인트", "세 번째 분석 포인트");
  }

  private News createNews() {
    return News.createPublished(
        "keyword news",
        "content",
        "description",
        "image.png",
        NewsSource.DART,
        "keyword-news",
        "https://example.com/keyword-news",
        Category.STOCK,
        Instant.parse("2026-08-17T10:00:00Z"));
  }
}
