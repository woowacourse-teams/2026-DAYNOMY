package org.grit.daynomy.keyword.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.keyword.domain.KeywordCategory;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
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
        newsKeywordRepository.save(createKeyword(news, KeywordCategory.POLICY, "기준금리"));
    newsKeywordRepository.save(createKeyword(otherNews, KeywordCategory.TERM, "환율"));

    var keywords = newsKeywordRepository.findByNewsIdOrderByIdAsc(news.getId());

    assertThat(keywords)
        .extracting(NewsKeyword::getId)
        .containsExactly(first.getId(), second.getId());
    assertThat(keywords).extracting(NewsKeyword::getKeyword).containsExactly("금리 인하", "기준금리");
    assertThat(keywords)
        .extracting(NewsKeyword::getCategory)
        .containsExactly(KeywordCategory.POLICY, KeywordCategory.POLICY);
    assertThat(keywords.get(0).getDefinition()).isEqualTo("키워드의 쉬운 뜻");
  }

  private NewsKeyword createKeyword(News news, KeywordCategory category, String keyword) {
    return new NewsKeyword(news, category, keyword, "키워드의 쉬운 뜻", "기사에서 키워드가 갖는 의미", "추가로 확인할 사항");
  }

  private News createNews(String title) {
    return News.createPublished(
        title,
        "content",
        "image.png",
        List.of(new NewsSourceInfo("DART", "https://example.com/" + title.replace(" ", "-"))),
        Category.STOCK,
        Instant.parse("2026-08-17T10:00:00Z"));
  }
}
