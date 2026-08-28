package org.grit.daynomy.asset.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
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
class AssetRankingHistoryRepositoryTest {

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

  @Autowired private AssetRankingHistoryRepository assetRankingHistoryRepository;

  @Test
  @DisplayName("종목 검색 쿼리는 기준일과 150위 이내에서 종목명·종목코드·페이징을 적용한다")
  void searchByRankedDateAndKeyword() {
    LocalDate rankedDate = LocalDate.of(2026, 8, 21);
    persistRanking("에코프로비엠", "247540", 1, rankedDate);
    persistRanking("에코프로", "086520", 2, rankedDate);
    persistRanking("에코프로머티", "450080", 151, rankedDate);
    persistRanking("에코프로과거", "000001", 1, rankedDate.minusDays(1));
    entityManager.flush();
    entityManager.clear();

    var pageable = PageRequest.of(0, 1, Sort.by(Sort.Direction.ASC, "ranking"));
    var nameResults =
        assetRankingHistoryRepository.searchByRankedDateAndKeyword(rankedDate, "에코프로", pageable);
    var codeResults =
        assetRankingHistoryRepository.searchByRankedDateAndKeyword(
            rankedDate, "0865", PageRequest.of(0, 20));

    assertThat(nameResults.getContent())
        .extracting(AssetRankingHistory::getRanking)
        .containsExactly(1);
    assertThat(nameResults.getTotalElements()).isEqualTo(2);
    assertThat(nameResults.getTotalPages()).isEqualTo(2);
    assertThat(codeResults.getContent())
        .singleElement()
        .extracting(AssetRankingHistory::getRanking)
        .isEqualTo(2);
  }

  @Test
  @DisplayName("종목 검색 쿼리는 LIKE 문자를 와일드카드가 아닌 일반 문자로 처리한다")
  void searchTreatsLikeCharactersAsLiterals() {
    LocalDate rankedDate = LocalDate.of(2026, 8, 21);
    persistRanking("코스닥%종목", "000001", 1, rankedDate);
    persistRanking("코스닥_종목", "000002", 2, rankedDate);
    persistRanking("코스닥!종목", "000003", 3, rankedDate);
    persistRanking("코스닥일반", "000004", 4, rankedDate);
    entityManager.flush();
    entityManager.clear();

    var pageable = PageRequest.of(0, 20);
    var percentResults =
        assetRankingHistoryRepository.searchByRankedDateAndKeyword(rankedDate, "코스닥!%", pageable);
    var underscoreResults =
        assetRankingHistoryRepository.searchByRankedDateAndKeyword(rankedDate, "코스닥!_", pageable);
    var escapeResults =
        assetRankingHistoryRepository.searchByRankedDateAndKeyword(rankedDate, "코스닥!!", pageable);

    assertThat(percentResults.getContent())
        .singleElement()
        .extracting(ranking -> ranking.getAsset().getName())
        .isEqualTo("코스닥%종목");
    assertThat(underscoreResults.getContent())
        .singleElement()
        .extracting(ranking -> ranking.getAsset().getName())
        .isEqualTo("코스닥_종목");
    assertThat(escapeResults.getContent())
        .singleElement()
        .extracting(ranking -> ranking.getAsset().getName())
        .isEqualTo("코스닥!종목");
  }

  private void persistRanking(String name, String code, int ranking, LocalDate rankedDate) {
    Asset asset = entityManager.persist(new Asset(name, AssetCategory.STOCK, code));
    entityManager.persist(new AssetRankingHistory(asset, ranking, rankedDate));
  }
}
