package org.grit.daynomy.portfolio.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.StockDailyPrice;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.StockDailyPriceRepository;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCheckRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisEvidenceRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisTransactionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SavedPortfolioAnalysisAcceptanceTest {

  private static final String API = "/api/users/me/portfolio/analysis";
  private static final String LEGACY = "/api/portfolio/analysis";

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

  @LocalServerPort private int port;
  @MockitoBean private PortfolioAnalysisAiClient ai;
  @Autowired private MemberRepository members;
  @Autowired private PortfolioRepository portfolios;
  @Autowired private PortfolioHoldingRepository holdings;
  @Autowired private AssetRepository assets;
  @Autowired private StockDailyPriceRepository prices;
  @Autowired private PortfolioAnalysisRepository analyses;
  @Autowired private PortfolioAnalysisEvidenceRepository evidence;
  @Autowired private PortfolioAnalysisCheckRepository checks;
  @Autowired private SavedPortfolioAnalysisTransactionService transactions;
  @Autowired private JwtTokenProvider tokens;
  @Autowired private JdbcTemplate jdbc;

  @Test
  void storesAndRestoresAnalysisAndReusesIdenticalAssetsWithoutCallingAiOrAdvancingCheckTime() {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    successfulAi();

    Response first = client(member).post(API);
    first
        .then()
        .statusCode(200)
        .body("status", equalTo("READY"))
        .body("matchesCurrentPortfolio", equalTo(true))
        .body("analysis.impacts[0].assetName", equalTo("삼성전자"));
    long id = first.jsonPath().getLong("analysisId");
    String checkedAt = first.jsonPath().getString("lastSucceededAt");
    String analyzedAt = first.jsonPath().getString("analysis.analyzedAt");

    client(member)
        .get(API)
        .then()
        .statusCode(200)
        .body("analysisId", equalTo((int) id))
        .body("lastSucceededAt", equalTo(checkedAt))
        .body("analysis.analyzedAt", equalTo(analyzedAt))
        .body("analysis.sources[0].url", equalTo("https://example.invalid/evidence"))
        .body("analysis.sources.size()", equalTo(2))
        .body("analysis.impacts[0].sources.size()", equalTo(1));
    client(member)
        .post(API)
        .then()
        .statusCode(200)
        .body("analysisId", equalTo((int) id))
        .body("lastSucceededAt", equalTo(checkedAt));
    client(member)
        .body(Map.of("assets", List.of(Map.of("assetName", "삼성전자", "weight", 100.00))))
        .post(LEGACY)
        .then()
        .statusCode(200)
        .body("analyzedAt", equalTo(analyzedAt));

    verify(ai).analyze(anyList());
    assertThat(
            evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(id, member.getId()))
        .hasSize(2);
  }

  @Test
  void newMemberDoesNotImportGuestAssetsAndOtherMembersCannotReadStoredAnalysis() {
    Member first = member();
    hold(portfolio(first), "삼성전자");
    Member second = member();
    successfulAi();
    long id =
        client(first).post(API).then().statusCode(200).extract().jsonPath().getLong("analysisId");

    client(second)
        .get(API)
        .then()
        .statusCode(200)
        .body("status", equalTo("EMPTY"))
        .body("analysis", nullValue());
    client(second)
        .body(Map.of("assets", List.of(Map.of("assetName", "삼성전자", "weight", 100))))
        .post(LEGACY)
        .then()
        .statusCode(409)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_ASSETS_UNAVAILABLE"));
    assertThat(analyses.findByIdAndPortfolioMemberId(id, second.getId())).isEmpty();
    assertThat(portfolios.findByMemberId(second.getId())).isEmpty();
    verify(ai).analyze(anyList());
  }

  @Test
  void changedHoldingsAreMarkedAndFailedAnalysisDoesNotReplacePreviousResultOrSuccessTime() {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    successfulAi();
    Response original = client(member).post(API);
    original.then().statusCode(200);
    long oldId = original.jsonPath().getLong("analysisId");
    String oldSuccess = original.jsonPath().getString("lastSucceededAt");
    hold(portfolio, "SK하이닉스");
    client(member)
        .get(API)
        .then()
        .statusCode(200)
        .body("status", equalTo("PORTFOLIO_CHANGED"))
        .body("matchesCurrentPortfolio", equalTo(false));

    when(ai.analyze(anyList()))
        .thenThrow(new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED));
    client(member).post(API).then().statusCode(502);
    client(member)
        .get(API)
        .then()
        .statusCode(200)
        .body("status", equalTo("FAILED"))
        .body("analysisId", equalTo((int) oldId))
        .body("lastSucceededAt", equalTo(oldSuccess))
        .body("failureCode", equalTo("AI_PORTFOLIO_ANALYSIS_FAILED"))
        .body("analysis.impacts.size()", equalTo(1));

    successfulAi();
    Response updated = client(member).post(API);
    updated
        .then()
        .statusCode(200)
        .body("status", equalTo("READY"))
        .body("analysis.impacts.size()", equalTo(2))
        .body("changeReason", equalTo("보유 자산 또는 비중 변경"));
    long newId = updated.jsonPath().getLong("analysisId");
    assertThat(newId).isNotEqualTo(oldId);
    assertThat(
            analyses
                .findByIdAndPortfolioMemberId(newId, member.getId())
                .orElseThrow()
                .getPreviousAnalysisId())
        .isEqualTo(oldId);
    assertThat(analyses.findByIdAndPortfolioMemberId(oldId, member.getId())).isPresent();
  }

  @Test
  void concurrentRequestsRunAiOnceAndDoNotHoldDatabaseTransactionDuringAiCall() throws Exception {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    CountDownLatch started = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    when(ai.analyze(anyList()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              started.countDown();
              assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
              return result(invocation.getArgument(0));
            });
    RequestSpecification first = client(member);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var pending = executor.submit(() -> first.post(API));
      try {
        assertThat(started.await(15, TimeUnit.SECONDS)).isTrue();
        client(member).get(API).then().statusCode(200).body("status", equalTo("PROCESSING"));
        client(member)
            .post(API)
            .then()
            .statusCode(409)
            .body("code", equalTo("PORTFOLIO_ANALYSIS_IN_PROGRESS"));
      } finally {
        release.countDown();
      }
      pending.get(20, TimeUnit.SECONDS).then().statusCode(200);
    }
    verify(ai).analyze(anyList());
    assertThat(checks.findById(portfolio.getId()).orElseThrow().getRequestToken()).isNull();
  }

  @Test
  void rejectsResultWhenPortfolioChangesDuringAnalysisAndAllowsRetry() {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    when(ai.analyze(anyList()))
        .thenAnswer(
            invocation -> {
              hold(portfolio, "SK하이닉스");
              return result(invocation.getArgument(0));
            });
    client(member)
        .post(API)
        .then()
        .statusCode(409)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_ASSETS_CHANGED"));
    assertThat(analyses.findFirstByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(member.getId()))
        .isEmpty();
    assertThat(checks.findById(portfolio.getId()).orElseThrow().getRequestToken()).isNull();
    successfulAi();
    client(member).post(API).then().statusCode(200).body("analysis.impacts.size()", equalTo(2));
  }

  @Test
  void expiredWorkerCannotSaveOrClearNewWorkersLease() {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    var expired = transactions.prepare(member.getId(), null);
    jdbc.update(
        "update portfolio_analysis_checks set request_expires_at = now() - interval '1 second' where portfolio_id = ?",
        portfolio.getId());
    var current = transactions.prepare(member.getId(), null);

    assertThatThrownBy(
            () ->
                transactions.complete(
                    member.getId(),
                    expired.token(),
                    expired.assets(),
                    PortfolioAnalysisResponse.empty()))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(PortfolioErrorCode.PORTFOLIO_ANALYSIS_REQUEST_EXPIRED);
    transactions.fail(member.getId(), expired.token(), "PORTFOLIO_ANALYSIS_FAILED");
    var check = checks.findById(portfolio.getId()).orElseThrow();
    assertThat(check.getRequestToken()).isEqualTo(current.token());
    assertThat(check.getLastFailedAt()).isNull();
    assertThat(analyses.findFirstByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(member.getId()))
        .isEmpty();
  }

  @Test
  void authenticatedEndpointsRequireAuthenticationAndCsrfWhileGuestTrialRemainsOncePerSession() {
    given().port(port).get(API).then().statusCode(401);
    Member member = member();
    hold(portfolio(member), "삼성전자");
    given().port(port).cookie("access_token", accessToken(member)).post(API).then().statusCode(403);
    given()
        .port(port)
        .cookie("access_token", accessToken(member))
        .contentType("application/json")
        .body(Map.of("assets", List.of(Map.of("assetName", "삼성전자", "weight", 100))))
        .post(LEGACY)
        .then()
        .statusCode(403);
    verifyNoInteractions(ai);
    successfulAi();
    var body = Map.of("assets", List.of(Map.of("assetName", "비회원 종목", "weight", 100)));
    Response guest = given().port(port).contentType("application/json").body(body).post(LEGACY);
    guest.then().statusCode(200);
    given()
        .port(port)
        .cookies(guest.cookies())
        .contentType("application/json")
        .body(body)
        .post(LEGACY)
        .then()
        .statusCode(403)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_TRIAL_EXHAUSTED"));
    client(member).get(API).then().statusCode(200).body("analysis", nullValue());
    verify(ai).analyze(anyList());
  }

  @Test
  void legacyRequestCannotUseAssetsDifferentFromMembersSavedPortfolio() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    client(member)
        .body(Map.of("assets", List.of(Map.of("assetName", "비회원 종목", "weight", 100))))
        .post(LEGACY)
        .then()
        .statusCode(409)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_ASSETS_CHANGED"));
    verifyNoInteractions(ai);
  }

  private void successfulAi() {
    doAnswer(invocation -> result(invocation.getArgument(0))).when(ai).analyze(anyList());
  }

  private PortfolioAnalysisResult result(List<PortfolioAnalysisTarget> targets) {
    var source = new PortfolioAnalysisResult.Source("실적 발표", "https://example.invalid/evidence");
    List<PortfolioAnalysisResult.AssetImpactResult> impacts = new java.util.ArrayList<>();
    for (int index = 0; index < targets.size(); index++) {
      impacts.add(
          new PortfolioAnalysisResult.AssetImpactResult(
              targets.get(index).assetName(),
              ImpactDirection.NEGATIVE,
              ImpactLevel.HIGH,
              "실적이 감소했어요.",
              "실적 부담이 있어요.",
              "다음 실적을 확인해 보세요.",
              "이익 감소를 반영했어요.",
              "영업이익이 감소했어요.",
              List.of(source),
              index + 1));
    }
    return new PortfolioAnalysisResult(
        "실적 감소가 부정적으로 작용해요.",
        impacts,
        List.of(
            source,
            new PortfolioAnalysisResult.Source("전체 분석 출처", "https://example.invalid/overall")));
  }

  private Member member() {
    String key = UUID.randomUUID().toString();
    return members.save(
        Member.createGoogleMember(key, key + "@example.invalid", key.substring(0, 12), null));
  }

  private Portfolio portfolio(Member member) {
    return portfolios.save(Portfolio.create(member));
  }

  private void hold(Portfolio portfolio, String name) {
    String key = UUID.randomUUID().toString();
    Asset asset =
        assets.save(
            Asset.listedStock(
                name,
                key,
                StockMarket.KOSPI,
                "KR" + key.replace("-", "").substring(0, 10),
                LocalDate.of(2026, 1, 1)));
    holdings.save(new PortfolioHolding(portfolio, asset, 1, new BigDecimal("100")));
    prices.save(new StockDailyPrice(asset, LocalDate.of(2026, 10, 12), new BigDecimal("100")));
  }

  private String accessToken(Member member) {
    return tokens.createTokenPair(member.getId(), MemberRole.USER).accessToken();
  }

  private RequestSpecification client(Member member) {
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .cookie("access_token", accessToken(member))
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }
}
