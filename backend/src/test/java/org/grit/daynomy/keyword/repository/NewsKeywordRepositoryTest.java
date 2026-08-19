package org.grit.daynomy.keyword.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import org.grit.daynomy.keyword.domain.NewsKeyword;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
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
    NewsKeyword first = newsKeywordRepository.save(new NewsKeyword(news, "금리 인하", "대출 수요 회복"));
    NewsKeyword second = newsKeywordRepository.save(new NewsKeyword(news, "부동산 규제", "거래량 회복"));
    newsKeywordRepository.save(new NewsKeyword(otherNews, "환율", "다른 뉴스 키워드"));

    var keywords = newsKeywordRepository.findByNewsIdOrderByIdAsc(news.getId());

    assertThat(keywords)
        .extracting(NewsKeyword::getId)
        .containsExactly(first.getId(), second.getId());
    assertThat(keywords).extracting(NewsKeyword::getKeyword).containsExactly("금리 인하", "부동산 규제");
  }

  private News createNews(String title) {
    return new News(
        title,
        "content",
        "description",
        "image.png",
        Category.STOCK,
        LocalDateTime.of(2026, 8, 17, 10, 0));
  }
}
