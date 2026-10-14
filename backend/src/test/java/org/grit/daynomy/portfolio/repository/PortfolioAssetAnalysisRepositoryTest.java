package org.grit.daynomy.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
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
class PortfolioAssetAnalysisRepositoryTest {

  private static final Instant ANALYZED_AT = Instant.parse("2026-10-12T07:00:00Z");

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
  }

  @Autowired private TestEntityManager entityManager;
  @Autowired private PortfolioAssetAnalysisRepository repository;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  @Test
  @DisplayName("유효기간 경계와 종목명·분석 버전·모델이 일치하는 결과만 재사용한다")
  void selectsOnlyMatchingUnexpiredResults() throws Exception {
    save("삼성전자", "v1", "model1", ANALYZED_AT, ANALYZED_AT.plusSeconds(3600), "매출이 늘었어요.");
    save("삼성전자", "v2", "model1", ANALYZED_AT, ANALYZED_AT.plusSeconds(3600), "새 기준이에요.");
    save("삼성전자", "v1", "model2", ANALYZED_AT, ANALYZED_AT.plusSeconds(3600), "다른 모델이에요.");
    save("SK하이닉스", "v1", "model1", ANALYZED_AT, ANALYZED_AT.plusSeconds(3600), "매출이 늘었어요.");
    entityManager.clear();

    var valid =
        repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
            List.of("삼성전자"), "v1", "model1", ANALYZED_AT.plusSeconds(3599));
    assertThat(valid).hasSize(1);
    assertThat(valid.getFirst().getSnapshot().issueSummary()).isEqualTo("매출이 늘었어요.");
    assertThat(valid.getFirst().getSnapshot().sources().getFirst().occurredOn())
        .isEqualTo(LocalDate.of(2026, 10, 1));
    assertThat(valid.getFirst().getSnapshot().sources().getFirst().factQuote())
        .isEqualTo("Revenue increased.");
    assertThat(
            repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
                List.of("삼성전자"), "v1", "model1", ANALYZED_AT.plusSeconds(3600)))
        .isEmpty();
    assertThat(
            repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
                List.of("삼성전자"), "v3", "model1", ANALYZED_AT))
        .isEmpty();
    assertThat(
            repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
                List.of("삼성전자"), "v1", "model3", ANALYZED_AT))
        .isEmpty();
  }

  @Test
  @DisplayName("동일 키는 갱신하고 늦게 저장된 과거 결과로 최신 결과를 덮어쓰지 않는다")
  void upsertKeepsNewestResultWithoutDuplicateRows() throws Exception {
    save("삼성전자", "v1", "model", ANALYZED_AT, ANALYZED_AT.plusSeconds(3600), "첫 결과예요.");
    save(
        "삼성전자",
        "v1",
        "model",
        ANALYZED_AT.plusSeconds(2),
        ANALYZED_AT.plusSeconds(3602),
        "최신 결과예요.");
    save(
        "삼성전자",
        "v1",
        "model",
        ANALYZED_AT.plusSeconds(1),
        ANALYZED_AT.plusSeconds(3601),
        "과거 결과예요.");
    entityManager.clear();

    var rows =
        repository.findAllByAssetNameInAndAnalysisVersionAndModelAndExpiresAtAfter(
            List.of("삼성전자"), "v1", "model", ANALYZED_AT);
    assertThat(rows).hasSize(1);
    assertThat(rows.getFirst().getSnapshot().issueSummary()).isEqualTo("최신 결과예요.");
    assertThat(rows.getFirst().getAnalyzedAt()).isEqualTo(ANALYZED_AT.plusSeconds(2));
    assertThat(rows.getFirst().getExpiresAt()).isEqualTo(ANALYZED_AT.plusSeconds(3602));
  }

  private void save(
      String name,
      String version,
      String model,
      Instant analyzedAt,
      Instant expiresAt,
      String summary)
      throws Exception {
    var source =
        new PortfolioAnalysisResult.Source(
            "실적 발표",
            "https://example.com/earnings",
            LocalDate.of(2026, 10, 1),
            LocalDate.of(2026, 10, 1),
            "매출이 늘었어요.",
            "Revenue increased.");
    var snapshot =
        new PortfolioAnalysisResult.AssetImpactResult(
            name,
            ImpactDirection.POSITIVE,
            ImpactLevel.HIGH,
            summary,
            "실적에 유리해요.",
            "다음 실적을 확인해요.",
            "매출 증가를 반영했어요.",
            source.excerpt(),
            List.of(source),
            0);
    repository.upsert(
        name, version, model, mapper.writeValueAsString(snapshot), analyzedAt, expiresAt);
  }
}
