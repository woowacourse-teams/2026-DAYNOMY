package org.grit.daynomy.search.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsSourceInfo;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.search.domain.NewsSearchSort;
import org.grit.daynomy.search.domain.NewsSearchTerms;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NewsSearchRepositoryTest {

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"))
          .withDatabaseName("daynomy")
          .withUsername("daynomy")
          .withPassword("daynomy");

  @DynamicPropertySource
  static void configurePostgresql(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
  }

  @Autowired private TestEntityManager entityManager;

  @Autowired private NewsSearchRepository newsSearchRepository;

  @Test
  @DisplayName("공개 검색은 제목·본문에 나뉜 모든 단어를 찾고 필터·페이지 건수를 유지한다")
  void searchPublishedNewsByAllTerms() {
    News titleMatch =
        entityManager.persist(
            createNews(
                "기준금리 추가 인하",
                "일반 본문",
                "terms-title",
                Category.ETF,
                Instant.parse("2026-08-14T10:00:00Z")));
    News splitMatch =
        entityManager.persist(
            createNews(
                "기준금리 전망",
                "추가 인하 가능성",
                "terms-split",
                Category.ETF,
                Instant.parse("2026-08-14T11:00:00Z")));
    News reversedMatch =
        entityManager.persist(
            createNews(
                "인하를 검토하는 기준금리",
                "일반 본문",
                "terms-reversed",
                Category.STOCK,
                Instant.parse("2026-08-14T12:00:00Z")));
    entityManager.persist(
        createNews(
            "기준금리 전망",
            "인상 가능성",
            "terms-missing",
            Category.ETF,
            Instant.parse("2026-08-14T13:00:00Z")));
    entityManager.persist(News.createDraft("금리 인하 초안", "본문", null, List.of(), Category.ETF));
    entityManager.flush();

    var specification = specification("  금리\t인하\u2003금리  ", null, NewsSearchSort.LATEST);
    var firstPage = newsSearchRepository.findAll(specification, PageRequest.of(0, 2));
    var secondPage = newsSearchRepository.findAll(specification, PageRequest.of(1, 2));
    var pastLastPage = newsSearchRepository.findAll(specification, PageRequest.of(4, 2));
    var etfResults =
        newsSearchRepository.findAll(
            specification("인하 금리", Category.ETF, NewsSearchSort.LATEST), PageRequest.of(0, 10));

    assertThat(firstPage.getContent()).containsExactly(reversedMatch, splitMatch);
    assertThat(firstPage.getTotalElements()).isEqualTo(3);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(secondPage.getContent()).containsExactly(titleMatch);
    assertThat(secondPage.getTotalElements()).isEqualTo(3);
    assertThat(pastLastPage.getContent()).isEmpty();
    assertThat(pastLastPage.getTotalElements()).isEqualTo(3);
    assertThat(etfResults.getContent()).containsExactly(splitMatch, titleMatch);
    assertThat(
            newsSearchRepository
                .findAll(specification("금리", null, NewsSearchSort.LATEST), PageRequest.of(0, 10))
                .getTotalElements())
        .isEqualTo(4);
  }

  @Test
  @DisplayName("관련도 검색은 제목 일치 단어 수를 우선하며 발행일·ID로 동점을 정렬한다")
  void searchPublishedNewsRanksTitleMatchesAndBreaksTies() {
    Instant samePublishedAt = Instant.parse("2026-08-14T10:00:00Z");
    News firstTitle =
        entityManager.persist(
            createNews("금리 추가 인하 전망", "본문", "rank-title-1", Category.ETF, samePublishedAt));
    News secondTitle =
        entityManager.persist(
            createNews("인하 가능성과 기준금리", "본문", "rank-title-2", Category.ETF, samePublishedAt));
    News split =
        entityManager.persist(
            createNews(
                "기준금리 전망",
                "추가 인하",
                "rank-split",
                Category.ETF,
                Instant.parse("2026-08-14T11:00:00Z")));
    News body =
        entityManager.persist(
            createNews(
                "시장 전망",
                "금리 인하",
                "rank-body",
                Category.ETF,
                Instant.parse("2026-08-14T12:00:00Z")));
    entityManager.flush();

    var relevant = specification("금리 인하", null, NewsSearchSort.RELEVANCE);
    var firstPage = newsSearchRepository.findAll(relevant, PageRequest.of(0, 2));
    var secondPage = newsSearchRepository.findAll(relevant, PageRequest.of(1, 2));
    var latest =
        newsSearchRepository.findAll(
            specification("금리 인하", null, NewsSearchSort.LATEST), PageRequest.of(0, 10));

    assertThat(firstPage.getContent()).containsExactly(secondTitle, firstTitle);
    assertThat(firstPage.getTotalElements()).isEqualTo(4);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(secondPage.getContent()).containsExactly(split, body);
    assertThat(latest.getContent()).containsExactly(body, split, secondTitle, firstTitle);
  }

  @Test
  @DisplayName("공개 검색은 %, _, !와 따옴표를 일반 문자로 검색하고 영문 대소문자를 구분하지 않는다")
  void searchPublishedNewsTreatsSpecialCharactersAsLiterals() {
    Instant publishedAt = Instant.parse("2026-08-14T10:00:00Z");
    News literal =
        entityManager.persist(
            createNews("금%리_!'ETF 뉴스", "본문", "public-literal", Category.ETF, publishedAt));
    entityManager.persist(
        createNews("금리 ETF 뉴스", "일반 뉴스", "public-normal", Category.ETF, publishedAt));
    entityManager.persist(
        createNews("금%리X!'ETF 뉴스", "본문", "public-wildcard", Category.ETF, publishedAt));
    entityManager.flush();

    var results =
        newsSearchRepository.findAll(
            specification("금%리_!'etf ETF etf", null, NewsSearchSort.RELEVANCE),
            PageRequest.of(0, 10));

    assertThat(results.getContent()).containsExactly(literal);
  }

  @Test
  @DisplayName("공개 검색은 뉴스 발행·수정·삭제 결과를 즉시 반영한다")
  void searchPublishedNewsReflectsNewsChanges() {
    News news =
        entityManager.persist(News.createDraft("금리 인하 전망", "본문", null, List.of(), Category.ETF));
    var specification = specification("금리 인하", null, NewsSearchSort.RELEVANCE);
    var pageable = PageRequest.of(0, 10);
    assertThat(newsSearchRepository.findAll(specification, pageable).getContent()).isEmpty();

    news.publish();
    entityManager.flush();
    assertThat(newsSearchRepository.findAll(specification, pageable).getContent())
        .containsExactly(news);

    news.update("산업 전망", "관련 내용 없음", null, List.of(), Category.ETF);
    entityManager.flush();
    assertThat(newsSearchRepository.findAll(specification, pageable).getContent()).isEmpty();

    news.update("금리 전망", "인하 가능성", null, List.of(), Category.ETF);
    entityManager.flush();
    assertThat(newsSearchRepository.findAll(specification, pageable).getContent())
        .containsExactly(news);

    news.delete();
    entityManager.flush();
    assertThat(newsSearchRepository.findAll(specification, pageable).getContent()).isEmpty();
  }

  @Test
  @DisplayName("관리자 검색은 공개 검색의 다중 단어 규칙을 적용하지 않고 기존 구문 검색을 유지한다")
  void adminSearchRetainsPhraseMatching() {
    News phrase =
        entityManager.persist(News.createDraft("금리 인하 전망", "본문", null, List.of(), Category.ETF));
    entityManager.persist(News.createDraft("금리 추가 인하 전망", "본문", null, List.of(), Category.ETF));
    entityManager.flush();

    var results =
        newsSearchRepository.search(
            "금리 인하",
            null,
            null,
            PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt", "id")));

    assertThat(results.getContent()).containsExactly(phrase);
  }

  @Test
  @DisplayName("뉴스 검색 쿼리는 제목·본문을 검색하고 카테고리·정렬·페이징을 적용한다")
  void searchNewsByKeywordAndCategory() {
    entityManager.persist(
        createNews(
            "금리 제목 뉴스",
            "일반 본문",
            "title-match",
            Category.ETF,
            Instant.parse("2026-08-14T10:00:00Z")));
    entityManager.persist(
        createNews(
            "최신 본문 일치 뉴스",
            "금리 본문",
            "description-match",
            Category.ETF,
            Instant.parse("2026-08-14T12:00:00Z")));
    entityManager.persist(
        createNews(
            "본문 일치 뉴스",
            "금리 본문",
            "content-match",
            Category.STOCK,
            Instant.parse("2026-08-14T11:00:00Z")));
    entityManager.persist(
        News.createDraft(
            "금리 초안 뉴스",
            "금리 본문",
            "image.png",
            List.of(new NewsSourceInfo("DART", "https://example.com/draft-match")),
            Category.ETF));
    entityManager.flush();

    Sort latestFirst = Sort.by(Sort.Direction.DESC, "publishedAt", "id");
    var allResults =
        newsSearchRepository.search(
            "금리", null, NewsStatus.PUBLISHED, PageRequest.of(0, 10, latestFirst));
    var etfPage =
        newsSearchRepository.search(
            "금리", Category.ETF, NewsStatus.PUBLISHED, PageRequest.of(0, 1, latestFirst));

    assertThat(allResults.getContent())
        .extracting(News::getTitle)
        .containsExactly("최신 본문 일치 뉴스", "본문 일치 뉴스", "금리 제목 뉴스");
    assertThat(etfPage.getContent())
        .singleElement()
        .extracting(News::getTitle)
        .isEqualTo("최신 본문 일치 뉴스");
    assertThat(etfPage.getTotalElements()).isEqualTo(2);
    assertThat(etfPage.getTotalPages()).isEqualTo(2);
  }

  @Test
  @DisplayName("뉴스 검색 쿼리는 LIKE 와일드카드를 일반 문자로 검색한다")
  void searchNewsTreatsLikeWildcardsAsLiteralCharacters() {
    entityManager.persist(
        createNews(
            "금% 문자 뉴스",
            "퍼센트 문자 검색",
            "percent-match",
            Category.REAL_ESTATE,
            Instant.parse("2026-08-14T10:00:00Z")));
    entityManager.persist(
        createNews(
            "금_ 문자 뉴스",
            "밑줄 문자 검색",
            "underscore-match",
            Category.REAL_ESTATE,
            Instant.parse("2026-08-15T10:00:00Z")));
    entityManager.persist(
        createNews(
            "금리 일반 뉴스",
            "일반 검색",
            "normal-news",
            Category.STOCK,
            Instant.parse("2026-08-16T10:00:00Z")));
    entityManager.flush();

    PageRequest pageable = PageRequest.of(0, 20);
    var percentResults = newsSearchRepository.search("금!%", null, NewsStatus.PUBLISHED, pageable);
    var underscoreResults =
        newsSearchRepository.search("금!_", null, NewsStatus.PUBLISHED, pageable);

    assertThat(percentResults.getContent())
        .singleElement()
        .extracting(News::getTitle)
        .isEqualTo("금% 문자 뉴스");
    assertThat(underscoreResults.getContent())
        .singleElement()
        .extracting(News::getTitle)
        .isEqualTo("금_ 문자 뉴스");
  }

  @Test
  @DisplayName("관리자 검색은 모든 상태에서 제목·본문을 검색하고 필터와 페이지를 적용한다")
  void searchAdminNewsAcrossStatuses() {
    entityManager.persist(News.createDraft("금리 일반 뉴스", "본문", null, List.of(), Category.STOCK));
    entityManager.persist(
        createNews(
            "금% 발행 뉴스",
            "본문", "published-match", Category.STOCK, Instant.parse("2026-08-14T10:00:00Z")));
    News draft =
        entityManager.persist(News.createDraft("초안 뉴스", "금% 본문", null, List.of(), Category.STOCK));
    News rejected = News.createDraft("금% 반려 뉴스", "본문", null, List.of(), Category.STOCK);
    rejected.reject();
    entityManager.persist(rejected);
    News etf =
        entityManager.persist(News.createDraft("금% ETF 뉴스", "본문", null, List.of(), Category.ETF));
    entityManager.flush();

    Sort latestFirst = Sort.by(Sort.Direction.DESC, "createdAt", "id");
    var firstPage =
        newsSearchRepository.search("금!%", null, null, PageRequest.of(0, 2, latestFirst));
    var draftStock =
        newsSearchRepository.search(
            "금!%", Category.STOCK, NewsStatus.DRAFT, PageRequest.of(0, 2, latestFirst));
    var noResults =
        newsSearchRepository.search("없음", null, null, PageRequest.of(0, 2, latestFirst));

    assertThat(firstPage.getTotalElements()).isEqualTo(4);
    assertThat(firstPage.getTotalPages()).isEqualTo(2);
    assertThat(firstPage.getContent())
        .extracting(News::getId)
        .containsExactly(etf.getId(), rejected.getId());
    assertThat(draftStock.getContent()).singleElement().isSameAs(draft);
    assertThat(noResults.getContent()).isEmpty();
  }

  private News createNews(
      String title, String content, String externalId, Category category, Instant publishedAt) {
    return News.createPublished(
        title,
        content,
        "image.png",
        List.of(new NewsSourceInfo("DART", "https://example.com/" + externalId)),
        category,
        publishedAt);
  }

  private NewsSearchSpecification specification(
      String keyword, Category category, NewsSearchSort sort) {
    return new NewsSearchSpecification(NewsSearchTerms.from(keyword), category, sort);
  }
}
