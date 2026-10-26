package org.grit.daynomy.emailsubscription.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis.Status;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisContent;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisKey;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisVerification;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisVerification.SourceCheck;
import org.grit.daynomy.emailsubscription.service.CommonStockAnalysisService;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@DataJpaTest
@Import(CommonStockAnalysisService.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class CommonStockAnalysisRepositoryTest {

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

  private static final Instant AS_OF = Instant.parse("2026-10-19T00:00:00Z");
  private static final String URL = "https://example.com/releases/earnings-2026";
  @Autowired private TestEntityManager entityManager;
  @Autowired private AssetRepository assets;
  @Autowired private CommonStockAnalysisRepository analyses;
  @Autowired private CommonStockAnalysisService service;

  @Test
  void invalidationPersistsAndPreventsReuseOrVerificationResurrection() {
    var asset = asset("005935");
    var key = key(asset);
    var id = service.storePending(key, content("실적 변화"));
    var report = passedVerification(id);
    service.recordVerification(id, report);
    entityManager.flush();
    var result = service.invalidate(id, "공시 정정");
    entityManager.flush();
    entityManager.clear();
    assertThat(result.analysisId()).isEqualTo(id);
    assertThat(result.invalidatedAt()).isNotNull();
    assertThat(service.findReusable(key)).isEmpty();
    service.recordVerification(id, report);
    service.storePending(key, content("다른 결과"));
    entityManager.flush();
    entityManager.clear();
    var invalidated = analyses.findById(id).orElseThrow();
    assertThat(invalidated.getStatus()).isEqualTo(Status.INVALIDATED);
    assertThat(invalidated.getInvalidationReason()).isEqualTo("공시 정정");
    assertThat(invalidated.getVerification()).isEqualTo(report);
  }

  @Test
  void onlyVerifiedResultsWithTheExactKeyAreReusable() {
    Asset asset = asset("005930");
    var key = key(asset);
    Long id = service.storePending(key, content("매출이 늘었어요."));
    assertThat(service.findReusable(key)).isEmpty();
    service.recordVerification(id, passedVerification(id));
    entityManager.flush();
    entityManager.clear();

    var reused = service.findReusable(key).orElseThrow();
    assertThat(reused.getContent().issueSummary()).isEqualTo("매출이 늘었어요.");
    assertThat(reused.getVerification().sources().getFirst().originalContentHash())
        .isEqualTo("a".repeat(64));
    assertThat(
            service.findReusable(
                new CommonStockAnalysisKey(asset.getId(), "other", "v1", "e1", AS_OF)))
        .isEmpty();
    assertThat(
            service.findReusable(
                new CommonStockAnalysisKey(asset.getId(), "model", "v2", "e1", AS_OF)))
        .isEmpty();
    assertThat(
            service.findReusable(
                new CommonStockAnalysisKey(asset.getId(), "model", "v1", "e2", AS_OF)))
        .isEmpty();
    assertThat(
            service.findReusable(
                new CommonStockAnalysisKey(
                    asset.getId(), "model", "v1", "e1", AS_OF.plusSeconds(1))))
        .isEmpty();
  }

  @Test
  void differentShareClassesNeverShareTheSameStoredResult() {
    Asset ordinary = asset("005930");
    Asset preferred = asset("005935");
    Long id = service.storePending(key(ordinary), content("보통주 분석이에요."));
    service.recordVerification(id, passedVerification(id));

    assertThat(service.findReusable(key(ordinary))).isPresent();
    assertThat(service.findReusable(key(preferred))).isEmpty();
  }

  @Test
  void aNewEvidenceVersionIsPendingUntilItIsSeparatelyVerified() {
    Asset asset = asset("005930");
    Long id = service.storePending(key(asset), content("첫 분석이에요."));
    service.recordVerification(id, passedVerification(id));
    var newKey = new CommonStockAnalysisKey(asset.getId(), "model", "v1", "e2", AS_OF);
    Long nextId = service.storePending(newKey, content("새 근거의 분석이에요."));

    assertThat(nextId).isNotEqualTo(id);
    assertThat(service.findReusable(newKey)).isEmpty();
    assertThat(service.findReusable(key(asset))).isPresent();
  }

  @Test
  void duplicateInsertsPreserveTheFirstContentAndItsVerification() {
    Asset asset = asset("005930");
    Long first = service.storePending(key(asset), content("첫 결과예요."));
    service.recordVerification(first, passedVerification(first));

    Long duplicate = service.storePending(key(asset), content("덮어쓰려는 결과예요."));
    entityManager.flush();
    entityManager.clear();

    assertThat(duplicate).isEqualTo(first);
    var reused = service.findReusable(key(asset)).orElseThrow();
    assertThat(reused.getContent().issueSummary()).isEqualTo("첫 결과예요.");
    assertThat(reused.getStatus()).isEqualTo(Status.VERIFIED);
  }

  @Test
  void rejectedResultsRemainUnavailableAfterReloading() {
    Asset asset = asset("005930");
    Long id = service.storePending(key(asset), content("검증되지 않은 결과예요."));
    service.recordVerification(
        id,
        new CommonStockAnalysisVerification(
            analyses.findById(id).orElseThrow().getContentHash(), Instant.now(), List.of()));
    entityManager.flush();
    entityManager.clear();

    assertThat(service.findReusable(key(asset))).isEmpty();
    assertThat(analyses.findByKey(key(asset)).orElseThrow().getStatus()).isEqualTo(Status.REJECTED);
  }

  @Test
  void changedAssetIdentityDisablesPreviouslyVerifiedResults() {
    Asset asset = asset("005930");
    Long id = service.storePending(key(asset), content("분석 결과예요."));
    service.recordVerification(id, passedVerification(id));
    asset.synchronizeStock("이름", StockMarket.KOSDAQ, null, LocalDate.of(2026, 10, 19));
    entityManager.flush();

    assertThat(service.findReusable(key(asset))).isEmpty();
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentSavesReturnOnePendingResultWithoutOverwritingEachOther() throws Exception {
    Asset asset = asset("parallel-" + UUID.randomUUID());
    var key = key(asset);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          CompletableFuture.supplyAsync(
              () -> saveAfterStart(key, "첫 결과예요.", ready, start), executor);
      var second =
          CompletableFuture.supplyAsync(
              () -> saveAfterStart(key, "다른 결과예요.", ready, start), executor);
      assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
      start.countDown();

      assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
    } finally {
      start.countDown();
    }
    assertThat(service.findReusable(key)).isEmpty();
    assertThat(analyses.findByKey(key).orElseThrow().getContent().issueSummary())
        .isIn("첫 결과예요.", "다른 결과예요.");
  }

  private Long saveAfterStart(
      CommonStockAnalysisKey key, String summary, CountDownLatch ready, CountDownLatch start) {
    ready.countDown();
    try {
      if (!start.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("동시 저장 대기 시간 초과");
      }
      return service.storePending(key, content(summary));
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(exception);
    }
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void concurrentVerificationKeepsTheFirstCompletedReport() throws Exception {
    Asset asset = asset("verify-" + UUID.randomUUID());
    Long id = service.storePending(key(asset), content("매출이 늘었어요."));
    CountDownLatch start = new CountDownLatch(1);
    try (var executor = Executors.newFixedThreadPool(2)) {
      var first =
          executor.submit(
              () -> {
                start.await();
                service.recordVerification(id, passedVerification(id));
                return true;
              });
      var second =
          executor.submit(
              () -> {
                start.await();
                service.recordVerification(id, passedVerification(id));
                return true;
              });
      start.countDown();
      assertThat(first.get(10, TimeUnit.SECONDS)).isTrue();
      assertThat(second.get(10, TimeUnit.SECONDS)).isTrue();
    } finally {
      start.countDown();
    }
    assertThat(service.findReusable(key(asset)).orElseThrow().getStatus())
        .isEqualTo(Status.VERIFIED);
  }

  private Asset asset(String code) {
    return assets.saveAndFlush(
        Asset.listedStock("삼성전자", code, StockMarket.KOSPI, null, LocalDate.of(2026, 10, 16)));
  }

  private CommonStockAnalysisKey key(Asset asset) {
    return new CommonStockAnalysisKey(asset.getId(), "model", "v1", "e1", AS_OF);
  }

  private CommonStockAnalysisContent content(String summary) {
    return new CommonStockAnalysisContent(
        ImpactDirection.POSITIVE,
        ImpactLevel.HIGH,
        summary,
        "사업에 유리해요.",
        "다음 발표를 확인해요.",
        "실적 발표가 근거예요.",
        List.of(
            new Source(
                "실적", URL, LocalDate.of(2026, 10, 16), null, "매출이 늘었어요.", "Revenue increased.")));
  }

  private CommonStockAnalysisVerification passedVerification(Long id) {
    return new CommonStockAnalysisVerification(
        analyses.findById(id).orElseThrow().getContentHash(),
        Instant.now(),
        List.of(new SourceCheck(URL, "a".repeat(64), true, true, true, true)));
  }
}
