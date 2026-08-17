package org.grit.daynomy.news.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.dto.NewsSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class NewsSearchIntegrationTest {

  @Autowired private NewsSearchService newsSearchService;

  @Test
  @Sql(
      statements = {
        "INSERT INTO news (title, content, description, category, published_at, created_at, updated_at) VALUES ('기준금리 동결', '채권 가격 변동이 예상됩니다.', '금리 뉴스', 'BOND', '2026-08-14 10:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        "INSERT INTO news (title, content, description, category, published_at, created_at, updated_at) VALUES ('증시 반등', '기준금리가 주식시장에 영향을 줬습니다.', '주식 뉴스', 'STOCK', '2026-08-15 10:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
      })
  void searchesExistingNewsTableByKeywordAndCategory() {
    NewsSearchResponse result = newsSearchService.search("금", "BOND", 0, 20);

    assertThat(result.category()).isEqualTo(Category.BOND);
    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.content())
        .singleElement()
        .extracting(NewsSearchResponse.NewsItem::title)
        .isEqualTo("기준금리 동결");
  }
}
