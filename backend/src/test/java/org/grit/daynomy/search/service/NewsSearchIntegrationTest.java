package org.grit.daynomy.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.dto.NewsListItemResponse;
import org.grit.daynomy.search.dto.NewsSearchResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.transaction.annotation.Transactional;

@ActiveProfiles("test")
@SpringBootTest
@Transactional
class NewsSearchIntegrationTest {

  @Autowired private NewsSearchService newsSearchService;

  @Test
  void returnsEmptyPageWhenNewsTableIsEmpty() {
    NewsSearchResponse result = newsSearchService.search("금", null, 1, 20);

    assertThat(result.content()).isEmpty();
    assertThat(result.totalElements()).isZero();
    assertThat(result.totalPages()).isZero();
  }

  @Test
  @Sql(
      statements = {
        "INSERT INTO news (title, content, description, source, external_id, source_url, category, published_at, created_at, updated_at) VALUES ('기준금리 동결', '채권 가격 변동이 예상됩니다.', '금리 뉴스', 'BOK', 'search-bond-1', 'https://example.com/search-bond-1', 'BOND', '2026-08-14 10:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        "INSERT INTO news (title, content, description, source, external_id, source_url, category, published_at, created_at, updated_at) VALUES ('증시 반등', '기준금리가 주식시장에 영향을 줬습니다.', '주식 뉴스', 'DART', 'search-stock-1', 'https://example.com/search-stock-1', 'STOCK', '2026-08-15 10:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
      })
  void searchesExistingNewsTableByKeywordAndCategory() {
    NewsSearchResponse result = newsSearchService.search("금", Category.BOND, 1, 20);

    assertThat(result.totalElements()).isEqualTo(1);
    assertThat(result.content())
        .singleElement()
        .extracting(NewsListItemResponse::title)
        .isEqualTo("기준금리 동결");
  }

  @Test
  @Sql(
      statements = {
        "INSERT INTO news (title, content, source, external_id, source_url, category, published_at, created_at, updated_at) VALUES ('금% 문자 뉴스', '퍼센트 문자 검색', 'KOSIS', 'search-gold-percent', 'https://example.com/search-gold-percent', 'GOLD', '2026-08-14 10:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        "INSERT INTO news (title, content, source, external_id, source_url, category, published_at, created_at, updated_at) VALUES ('금_ 문자 뉴스', '밑줄 문자 검색', 'KOSIS', 'search-gold-underscore', 'https://example.com/search-gold-underscore', 'GOLD', '2026-08-15 10:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
        "INSERT INTO news (title, content, source, external_id, source_url, category, published_at, created_at, updated_at) VALUES ('금리 일반 뉴스', '일반 검색', 'BOK', 'search-bond-normal', 'https://example.com/search-bond-normal', 'BOND', '2026-08-16 10:00:00', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
      })
  void treatsLikeWildcardsAsLiteralCharacters() {
    NewsSearchResponse percentResult = newsSearchService.search("금%", null, 1, 20);
    NewsSearchResponse underscoreResult = newsSearchService.search("금_", null, 1, 20);

    assertThat(percentResult.content())
        .singleElement()
        .extracting(NewsListItemResponse::title)
        .isEqualTo("금% 문자 뉴스");
    assertThat(underscoreResult.content())
        .singleElement()
        .extracting(NewsListItemResponse::title)
        .isEqualTo("금_ 문자 뉴스");
  }
}
