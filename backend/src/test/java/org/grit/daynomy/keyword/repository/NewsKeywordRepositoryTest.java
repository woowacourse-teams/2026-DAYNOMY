package org.grit.daynomy.keyword.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSource;
import org.grit.daynomy.news.repository.NewsRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@ActiveProfiles("test")
@SpringBootTest
class NewsKeywordRepositoryTest {

  @Autowired private NewsRepository newsRepository;

  @Autowired private NewsKeywordRepository newsKeywordRepository;

  @BeforeEach
  void setUp() {
    newsKeywordRepository.deleteAll();
    newsRepository.deleteAll();
  }

  @Test
  @DisplayName("뉴스 ID로 키워드를 ID 오름차순 조회한다")
  void findByNewsIdReturnsKeywordsOrderByIdAsc() {
    News news = newsRepository.save(createNews("target news"));
    News otherNews = newsRepository.save(createNews("other news"));
    NewsKeyword first =
        newsKeywordRepository.save(createKeyword(news, KeywordCategory.POLICY, "금리 인하"));
    NewsKeyword second =
        newsKeywordRepository.save(createKeyword(news, KeywordCategory.POLICY, "부동산 규제"));
    newsKeywordRepository.save(createKeyword(otherNews, KeywordCategory.TERM, "환율"));

    var keywords = newsKeywordRepository.findByNewsIdOrderByIdAsc(news.getId());

    assertThat(keywords)
        .extracting(NewsKeyword::getId)
        .containsExactly(first.getId(), second.getId());
    assertThat(keywords).extracting(NewsKeyword::getKeyword).containsExactly("금리 인하", "부동산 규제");
    assertThat(keywords)
        .extracting(NewsKeyword::getCategory)
        .containsExactly(KeywordCategory.POLICY, KeywordCategory.POLICY);
    assertThat(keywords.get(0).getPoint1()).isEqualTo("첫 번째 분석 포인트");
  }

  private NewsKeyword createKeyword(News news, KeywordCategory category, String keyword) {
    return new NewsKeyword(news, category, keyword, "첫 번째 분석 포인트", "두 번째 분석 포인트", "세 번째 분석 포인트");
  }

  private News createNews(String title) {
    return new News(
        title,
        "content",
        "description",
        "image.png",
        NewsSource.DART,
        title,
        "https://example.com/" + title.replace(" ", "-"),
        Category.STOCK,
        Instant.parse("2026-08-17T10:00:00Z"));
  }
}
