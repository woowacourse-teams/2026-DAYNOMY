package org.grit.daynomy.portfolio.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisCheck;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisEvidence;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSnapshot;
import org.grit.daynomy.portfolio.domain.PortfolioImpactSummary;
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
class PortfolioAnalysisRepositoryTest {

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
  @Autowired private PortfolioAnalysisRepository analyses;
  @Autowired private PortfolioAnalysisEvidenceRepository evidence;
  @Autowired private PortfolioAnalysisCheckRepository checks;

  @Test
  @DisplayName("분석 당시 자산과 결과를 JSON 문자열이 아닌 JSONB 객체로 보존한다")
  void roundTripsSnapshotAsJsonbObject() {
    Portfolio portfolio = portfolio();
    PortfolioAnalysis analysis = save(portfolio, null, ANALYZED_AT, "최초 분석");
    PortfolioAnalysisSnapshot expected = analysis.getSnapshot();
    entityManager.flush();
    entityManager.clear();

    PortfolioAnalysis restored =
        analyses
            .findByIdAndPortfolioMemberId(analysis.getId(), portfolio.getMember().getId())
            .orElseThrow();

    assertThat(restored.getSnapshot()).isEqualTo(expected);
    assertThat(restored.getSchemaVersion()).isEqualTo(1);
    Object type =
        entityManager
            .getEntityManager()
            .createNativeQuery(
                "select jsonb_typeof(snapshot) from portfolio_analyses where id = :id")
            .setParameter("id", analysis.getId())
            .getSingleResult();
    assertThat(type).isEqualTo("object");
  }

  @Test
  @DisplayName("최신 분석과 근거 조회는 회원별로 격리하고 같은 시각이면 ID 순으로 조회한다")
  void isolatesMembersAndUsesStableLatestOrdering() {
    Portfolio first = portfolio();
    Portfolio second = portfolio();
    PortfolioAnalysis older = save(first, null, ANALYZED_AT, "최초 분석");
    PortfolioAnalysis latest = save(first, older, ANALYZED_AT, "정정 발표 반영");
    PortfolioAnalysis other = save(second, null, ANALYZED_AT.plusSeconds(10), "최초 분석");
    saveEvidence(latest);
    entityManager.flush();
    entityManager.clear();

    Long firstMemberId = first.getMember().getId();
    Long secondMemberId = second.getMember().getId();
    assertThat(analyses.findFirstByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(firstMemberId))
        .get()
        .extracting(PortfolioAnalysis::getId)
        .isEqualTo(latest.getId());
    assertThat(analyses.findFirstByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(secondMemberId))
        .get()
        .extracting(PortfolioAnalysis::getId)
        .isEqualTo(other.getId());
    assertThat(analyses.findByIdAndPortfolioMemberId(latest.getId(), secondMemberId)).isEmpty();
    assertThat(
            evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                latest.getId(), secondMemberId))
        .isEmpty();
    assertThat(
            evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                latest.getId(), firstMemberId))
        .singleElement()
        .satisfies(
            source -> {
              assertThat(source.getPublishedOn()).isEqualTo(LocalDate.of(2026, 10, 12));
              assertThat(source.getOccurredOn()).isEqualTo(LocalDate.of(2026, 10, 9));
            });
    assertThat(analyses.findByIdAndPortfolioMemberId(older.getId(), firstMemberId))
        .get()
        .extracting(PortfolioAnalysis::getChangeReason)
        .isEqualTo("최초 분석");
  }

  @Test
  @DisplayName("여러 번 확인해도 상태는 한 행이고 분석 생성 시각과 내용은 유지한다")
  void updatesCheckWithoutAppendingAnalysisOrLosingSuccessOnFailure() {
    Portfolio portfolio = portfolio();
    PortfolioAnalysis analysis = save(portfolio, null, ANALYZED_AT, "최초 분석");
    PortfolioAnalysisCheck check = checks.save(new PortfolioAnalysisCheck(portfolio));
    check.recordSuccess(ANALYZED_AT);
    entityManager.flush();
    entityManager.clear();

    check = checks.findById(portfolio.getId()).orElseThrow();
    check.recordSuccess(ANALYZED_AT.plusSeconds(60));
    check.recordFailure(ANALYZED_AT.plusSeconds(120), "EXTERNAL_API_ERROR");
    entityManager.flush();
    entityManager.clear();

    PortfolioAnalysisCheck restored = checks.findById(portfolio.getId()).orElseThrow();
    assertThat(checks.count()).isEqualTo(1);
    assertThat(analyses.count()).isEqualTo(1);
    assertThat(restored.getLastSucceededAt()).isEqualTo(ANALYZED_AT.plusSeconds(60));
    assertThat(restored.getLastFailedAt()).isEqualTo(ANALYZED_AT.plusSeconds(120));
    assertThat(
            analyses.findByIdAndPortfolioMemberId(analysis.getId(), portfolio.getMember().getId()))
        .get()
        .extracting(PortfolioAnalysis::getAnalyzedAt)
        .isEqualTo(ANALYZED_AT);
  }

  @Test
  @DisplayName("오래된 분석 삭제는 그 근거만 삭제하고 다음 분석을 보존한다")
  void deletesOldEvidenceAndClearsPreviousReferenceWithoutDeletingLatest() {
    Portfolio portfolio = portfolio();
    PortfolioAnalysis old = save(portfolio, null, ANALYZED_AT, "최초 분석");
    PortfolioAnalysis latest = save(portfolio, old, ANALYZED_AT.plusSeconds(60), "새 계약 반영");
    saveEvidence(old);
    saveEvidence(latest);
    entityManager.flush();
    entityManager.clear();

    analyses.deleteById(old.getId());
    analyses.flush();
    entityManager.clear();

    PortfolioAnalysis restored = analyses.findById(latest.getId()).orElseThrow();
    assertThat(restored.getPreviousAnalysisId()).isNull();
    assertThat(restored.getChangeReason()).isEqualTo("새 계약 반영");
    assertThat(evidence.count()).isEqualTo(1);
    assertThat(
            evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                latest.getId(), portfolio.getMember().getId()))
        .hasSize(1);
  }

  private Portfolio portfolio() {
    String key = UUID.randomUUID().toString();
    Member member =
        entityManager.persist(
            Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
    return entityManager.persist(Portfolio.create(member));
  }

  private PortfolioAnalysis save(
      Portfolio portfolio, PortfolioAnalysis previous, Instant time, String reason) {
    return analyses.save(
        PortfolioAnalysis.create(
            portfolio, previous, "portfolio-v1", "test-model", snapshot(), time, reason));
  }

  private void saveEvidence(PortfolioAnalysis analysis) {
    evidence.save(
        new PortfolioAnalysisEvidence(
            analysis,
            "테스트 종목",
            "실적 발표",
            "https://example.invalid/earnings",
            LocalDate.of(2026, 10, 12),
            LocalDate.of(2026, 10, 9),
            "영업이익이 감소했어요."));
  }

  private PortfolioAnalysisSnapshot snapshot() {
    BigDecimal weight = new BigDecimal("100.00");
    return new PortfolioAnalysisSnapshot(
        List.of(new PortfolioAnalysisSnapshot.Asset("테스트 종목", weight)),
        PortfolioImpactSummary.calculate(
            List.of(
                new PortfolioImpactSummary.AssetImpact(
                    weight, ImpactDirection.NEGATIVE, ImpactLevel.HIGH))),
        "실적 감소가 부정적인 영향을 줄 수 있어요.",
        List.of(
            new PortfolioAnalysisSnapshot.AssetImpact(
                "테스트 종목",
                ImpactDirection.NEGATIVE,
                ImpactLevel.HIGH,
                "영업이익이 감소했어요.",
                "실적 부담이 예상돼요.",
                "다음 실적을 확인해 보세요.",
                "이익 감소를 반영했어요.",
                "영업이익이 감소했어요.",
                1)));
  }
}
