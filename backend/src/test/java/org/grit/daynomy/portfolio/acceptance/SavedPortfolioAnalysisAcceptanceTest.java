package org.grit.daynomy.portfolio.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.time.Instant;
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
import org.grit.daynomy.portfolio.v1.ai.OpenAiPortfolioAnalysisClientV1;
import org.grit.daynomy.portfolio.v1.ai.PortfolioAnalysisTargetV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
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
  private static final String GUEST = LEGACY + "/tasks";

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
  @Autowired private io.micrometer.core.instrument.MeterRegistry metrics;

  @Autowired
  private org.springframework.boot.web.server.servlet.context.ServletWebServerApplicationContext
      serverContext;

  @MockitoBean private PortfolioAnalysisAiClient ai;
  @MockitoBean private OpenAiPortfolioAnalysisClientV1 v1Ai;
  @MockitoBean private org.grit.daynomy.portfolio.ai.PortfolioAnalysisNarrative narrative;

  @org.junit.jupiter.api.BeforeEach
  void narrativeStub() {
    assetAnalyses.deleteAll();
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              List<PortfolioAnalysisTargetV1> targets = invocation.getArgument(0);
              return result(
                  targets.stream()
                      .map(target -> new PortfolioAnalysisTarget(target.assetName()))
                      .toList());
            })
        .when(v1Ai)
        .analyze(anyList());
    when(narrative.summarize(org.mockito.ArgumentMatchers.any()))
        .thenReturn(
            "현재 보유한 종목의 비중을 확인했어요.\n\n실적과 비용이 전체 자산에 미치는 영향을 함께 살펴봐야 해요.\n\n비중이 큰 종목의 다음 실적 발표를 확인해 보세요.");
  }

  @Autowired
  private org.grit.daynomy.portfolio.repository.PortfolioAssetAnalysisRepository assetAnalyses;

  @Autowired private MemberRepository members;
  @Autowired private PortfolioRepository portfolios;
  @Autowired private PortfolioHoldingRepository holdings;
  @Autowired private AssetRepository assets;
  @MockitoSpyBean private StockDailyPriceRepository prices;
  @MockitoSpyBean private PortfolioAnalysisRepository analyses;
  @Autowired private PortfolioAnalysisEvidenceRepository evidence;
  @Autowired private PortfolioAnalysisCheckRepository checks;
  @Autowired private SavedPortfolioAnalysisTransactionService transactions;
  @Autowired private JwtTokenProvider tokens;
  @Autowired private JdbcTemplate jdbc;

  @ParameterizedTest
  @CsvSource({"v1,2", "v2,2", "v1,50", "v2,50"})
  void versionedAnalysisUsesItsOwnPriceQueryThroughoutTheRequest(String version, int assetCount) {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    for (int index = 0; index < assetCount; index++) hold(portfolio, "인수시험 종목" + index);
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              return result(invocation.getArgument(0));
            })
        .when(ai)
        .analyze(anyList());
    clearInvocations(prices);

    var response =
        client(member)
            .post("/api/" + version + "/users/me/portfolio/analysis")
            .then()
            .statusCode(200)
            .extract()
            .response();

    assertThat(response.jsonPath().getString("status")).isEqualTo("READY");
    assertThat(response.jsonPath().getInt("analysis.totalAssetCount")).isEqualTo(assetCount);
    assertThat(response.jsonPath().getList("analysis.impacts.weight", BigDecimal.class))
        .allSatisfy(
            weight ->
                assertThat(weight)
                    .isEqualByComparingTo(
                        BigDecimal.valueOf(100).divide(BigDecimal.valueOf(assetCount))));
    var stored = analyses.findById(response.jsonPath().getLong("analysisId")).orElseThrow();
    assertThat(stored.getAnalysisVersion())
        .isEqualTo(
            version.equals("v1")
                ? "portfolio-v7-batch-price-v1"
                : "portfolio-v8-select5-price-ai-v2");
    if (version.equals("v1")) {
      // prepare와 complete에서 각각 현재 포트폴리오 가격을 확인한다.
      verify(prices, times(assetCount * 2))
          .findTop2ByAssetIdOrderByBaseDateDesc(org.mockito.ArgumentMatchers.anyLong());
      verify(prices, never()).findLatestTwoByAssetIds(anyList());
    } else {
      verify(prices, times(2)).findLatestTwoByAssetIds(anyList());
      verify(prices, never())
          .findTop2ByAssetIdOrderByBaseDateDesc(org.mockito.ArgumentMatchers.anyLong());
    }
    if (version.equals("v1")) {
      verify(v1Ai).analyze(anyList());
      verifyNoInteractions(ai);
      assertThat(assetAnalyses.count()).isZero();
    } else {
      verify(ai).analyze(anyList());
      verifyNoInteractions(v1Ai);
      assertThat(assetAnalyses.count()).isEqualTo(Math.min(5, assetCount));
      assertThat(response.jsonPath().getInt("analysis.selectedAssetCount"))
          .isEqualTo(Math.min(5, assetCount));
      assertThat(response.jsonPath().getList("analysis.excludedAssets"))
          .hasSize(Math.max(0, assetCount - 5));
      assertThat(stored.getSnapshot().assets()).hasSize(assetCount);
      assertThat(stored.getSnapshot().impacts()).hasSize(Math.min(5, assetCount));
      if (assetCount > 5) {
        assertThat(response.jsonPath().getString("analysis.coverage")).isEqualTo("PARTIAL");
        assertThat((Object) response.jsonPath().get("analysis.overallScore")).isNull();
      }
      long analysisId = response.jsonPath().getLong("analysisId");
      var repeated =
          client(member)
              .post("/api/v2/users/me/portfolio/analysis")
              .then()
              .statusCode(200)
              .extract()
              .response();
      assertThat(repeated.jsonPath().getLong("analysisId")).isEqualTo(analysisId);
      assertThat(repeated.jsonPath().getList("analysis.excludedAssets"))
          .hasSize(Math.max(0, assetCount - 5));
    }
  }

  @Test
  void versionedAnalysisDoesNotReuseTheOtherVersionsCompletedResult() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    successfulAi();
    String v1 = "/api/v1/users/me/portfolio/analysis";
    String v2 = "/api/v2/users/me/portfolio/analysis";

    long firstId =
        client(member).post(v1).then().statusCode(200).extract().jsonPath().getLong("analysisId");
    long secondId =
        client(member).post(v2).then().statusCode(200).extract().jsonPath().getLong("analysisId");
    long repeatedId =
        client(member).post(v2).then().statusCode(200).extract().jsonPath().getLong("analysisId");

    assertThat(secondId).isNotEqualTo(firstId);
    assertThat(repeatedId).isEqualTo(secondId);
    verify(v1Ai).analyze(anyList());
    verify(ai).analyze(anyList());
  }

  @ParameterizedTest
  @ValueSource(strings = {"v1", "v2"})
  void versionedEndpointsRequireAuthenticationAndCsrf(String version) {
    String path = "/api/" + version + "/users/me/portfolio/analysis";
    var csrf = given().port(port).get("/api/auth/csrf");
    given()
        .port(port)
        .cookies(csrf.cookies())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"))
        .post(path)
        .then()
        .statusCode(401);
    Member member = member();
    hold(portfolio(member), "삼성전자");
    given()
        .port(port)
        .cookie("access_token", accessToken(member))
        .post(path)
        .then()
        .statusCode(403);
    verifyNoInteractions(ai, v1Ai);
  }

  @Test
  void v1AlwaysAnalyzesEachMembersStocksWhileV2ReusesTheSharedResults() {
    successfulAi();
    var membersToTest = List.of(member(), member(), member(), member());
    for (var member : membersToTest) {
      var portfolio = portfolio(member);
      hold(portfolio, "삼성전자");
      hold(portfolio, "SK하이닉스");
    }
    client(membersToTest.get(0)).post("/api/v1/users/me/portfolio/analysis").then().statusCode(200);
    client(membersToTest.get(1)).post("/api/v1/users/me/portfolio/analysis").then().statusCode(200);
    assertThat(assetAnalyses.count()).isZero();
    client(membersToTest.get(2)).post("/api/v2/users/me/portfolio/analysis").then().statusCode(200);
    client(membersToTest.get(3)).post("/api/v2/users/me/portfolio/analysis").then().statusCode(200);

    verify(v1Ai, times(2)).analyze(anyList());
    verify(ai).analyze(anyList());
    verify(narrative, times(4)).summarize(org.mockito.ArgumentMatchers.any());
    assertThat(assetAnalyses.count()).isEqualTo(2);
  }

  @ParameterizedTest
  @ValueSource(strings = {"v1", "v2"})
  void concurrentMembersUseSeparateV1AnalysisButShareV2Analysis(String version) throws Exception {
    var firstMember = member();
    var secondMember = member();
    var firstPortfolio = portfolio(firstMember);
    var secondPortfolio = portfolio(secondMember);
    hold(firstPortfolio, "삼성전자");
    hold(secondPortfolio, "삼성전자");
    var entered = new CountDownLatch(version.equals("v1") ? 2 : 1);
    var release = new CountDownLatch(1);
    if (version.equals("v1")) {
      doAnswer(
              invocation -> {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                List<PortfolioAnalysisTargetV1> targets = invocation.getArgument(0);
                return result(
                    targets.stream()
                        .map(target -> new PortfolioAnalysisTarget(target.assetName()))
                        .toList());
              })
          .when(v1Ai)
          .analyze(anyList());
    } else {
      doAnswer(
              invocation -> {
                entered.countDown();
                assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
                return result(invocation.getArgument(0));
              })
          .when(ai)
          .analyze(anyList());
    }
    String path = "/api/" + version + "/users/me/portfolio/analysis";
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var first = executor.submit(() -> client(firstMember).post(path));
      var second = executor.submit(() -> client(secondMember).post(path));
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        boolean bothProcessing = false;
        while (System.nanoTime() < deadline) {
          bothProcessing =
              checks
                      .findById(firstPortfolio.getId())
                      .map(check -> check.getRequestToken() != null)
                      .orElse(false)
                  && checks
                      .findById(secondPortfolio.getId())
                      .map(check -> check.getRequestToken() != null)
                      .orElse(false);
          if (bothProcessing) break;
          Thread.sleep(10);
        }
        assertThat(bothProcessing).isTrue();
        release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
        assertThat(second.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
      } finally {
        release.countDown();
      }
    }
    if (version.equals("v1")) {
      verify(v1Ai, times(2)).analyze(anyList());
      verifyNoInteractions(ai);
      assertThat(assetAnalyses.count()).isZero();
    } else {
      verify(ai).analyze(anyList());
      verifyNoInteractions(v1Ai);
      assertThat(assetAnalyses.count()).isEqualTo(1);
    }
  }

  @Test
  void v2ServesOtherRequestsWhileMoreAnalysesThanTomcatThreadsAreWaiting() throws Exception {
    var server = (org.springframework.boot.tomcat.TomcatWebServer) serverContext.getWebServer();
    var protocol =
        (org.apache.coyote.AbstractProtocol<?>)
            server.getTomcat().getConnector().getProtocolHandler();
    int previousMax = protocol.getMaxThreads();
    int previousSpare = protocol.getMinSpareThreads();
    var entered = new CountDownLatch(4);
    var release = new CountDownLatch(1);
    doAnswer(
            invocation -> {
              assertThat(Thread.currentThread().isVirtual()).isTrue();
              entered.countDown();
              assertThat(release.await(15, TimeUnit.SECONDS)).isTrue();
              return result(invocation.getArgument(0));
            })
        .when(ai)
        .analyze(anyList());
    var clients = new java.util.ArrayList<RequestSpecification>();
    for (int index = 0; index < 4; index++) {
      var member = member();
      hold(portfolio(member), "비동기 시험 종목" + index);
      clients.add(client(member));
    }
    protocol.setMinSpareThreads(2);
    protocol.setMaxThreads(2);
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
      var requests =
          clients.stream()
              .map(
                  client ->
                      executor.submit(() -> client.post("/api/v2/users/me/portfolio/analysis")))
              .toList();
      try {
        assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(metrics.find("tomcat.threads.config.max").gauges())
            .anySatisfy(gauge -> assertThat(gauge.value()).isEqualTo(2));
        assertThat(metrics.find("tomcat.threads.busy").gauges()).isNotEmpty();
        assertThat(metrics.find("tomcat.threads.current").gauges()).isNotEmpty();
        var csrf = executor.submit(() -> given().port(port).get("/api/auth/csrf"));
        assertThat(csrf.get(5, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
      } finally {
        release.countDown();
      }
      for (var request : requests)
        assertThat(request.get(10, TimeUnit.SECONDS).statusCode()).isEqualTo(200);
    } finally {
      release.countDown();
      protocol.setMaxThreads(previousMax);
      protocol.setMinSpareThreads(previousSpare);
    }
  }

  @Test
  void v1IgnoresCompletedSharedResultsCreatedByV2() {
    successfulAi();
    var v2Member = member();
    var v1Member = member();
    hold(portfolio(v2Member), "삼성전자");
    hold(portfolio(v1Member), "삼성전자");
    client(v2Member).post("/api/v2/users/me/portfolio/analysis").then().statusCode(200);
    var stored = assetAnalyses.findAll().getFirst();

    client(v1Member).post("/api/v1/users/me/portfolio/analysis").then().statusCode(200);

    verify(v1Ai).analyze(anyList());
    verify(ai).analyze(anyList());
    assertThat(assetAnalyses.count()).isEqualTo(1);
    assertThat(assetAnalyses.findById(stored.getId()).orElseThrow().getAnalyzedAt())
        .isEqualTo(stored.getAnalyzedAt());
  }

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
        .body("analysis.impacts[0].sources[0].publishedOn", equalTo("2026-09-29"))
        .body("analysis.impacts[0].sources[0].occurredOn", equalTo("2026-09-25"))
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
  void differentMembersReuseAssetResultsButKeepTheirOwnWeightsAndSummaries() {
    Member first = member();
    Member second = member();
    var firstPortfolio = portfolio(first);
    var secondPortfolio = portfolio(second);
    hold(firstPortfolio, "삼성전자");
    hold(firstPortfolio, "SK하이닉스");
    hold(secondPortfolio, "삼성전자");
    hold(secondPortfolio, "SK하이닉스");
    var firstHolding = holdings.findAllByPortfolioIdOrderById(firstPortfolio.getId()).getFirst();
    firstHolding.change(4, firstHolding.getAveragePurchasePrice());
    holdings.saveAndFlush(firstHolding);
    var secondHolding = holdings.findAllByPortfolioIdOrderById(secondPortfolio.getId()).getLast();
    secondHolding.change(4, secondHolding.getAveragePurchasePrice());
    holdings.saveAndFlush(secondHolding);
    successfulAi();

    var firstResponse = client(first).post(API).then().statusCode(200).extract().response();
    var secondResponse = client(second).post(API).then().statusCode(200).extract().response();

    assertThat(
            firstResponse
                .jsonPath()
                .getDouble("analysis.impacts.find { it.assetName == '삼성전자' }.weight"))
        .isEqualTo(80.0);
    assertThat(
            secondResponse
                .jsonPath()
                .getDouble("analysis.impacts.find { it.assetName == '삼성전자' }.weight"))
        .isEqualTo(20.0);
    assertThat(firstResponse.jsonPath().getLong("analysisId"))
        .isNotEqualTo(secondResponse.jsonPath().getLong("analysisId"));
    verify(ai).analyze(anyList());
    verify(narrative, org.mockito.Mockito.times(2)).summarize(org.mockito.ArgumentMatchers.any());
    assertThat(assetAnalyses.count()).isEqualTo(2);
  }

  @Test
  void expiredSharedResultIsSearchedAgainForAnotherMember() {
    Member first = member();
    Member second = member();
    hold(portfolio(first), "삼성전자");
    hold(portfolio(second), "삼성전자");
    successfulAi();
    client(first).post(API).then().statusCode(200);
    jdbc.update(
        "UPDATE portfolio_asset_analyses SET analyzed_at = CURRENT_TIMESTAMP - INTERVAL '2 hours', expires_at = CURRENT_TIMESTAMP - INTERVAL '1 hour'");

    client(second).post(API).then().statusCode(200);

    verify(ai, org.mockito.Mockito.times(2)).analyze(anyList());
    assertThat(assetAnalyses.count()).isEqualTo(1);
    assertThat(assetAnalyses.findAll().getFirst().getExpiresAt()).isAfter(Instant.now());
  }

  @Test
  void explicitRefreshWithoutPreviousAnalysisStillSearchesEvenWithSharedResults() {
    Member first = member();
    Member second = member();
    hold(portfolio(first), "삼성전자");
    hold(portfolio(second), "삼성전자");
    successfulAi();
    client(first).post(API).then().statusCode(200);
    assertThat(assetAnalyses.count()).isEqualTo(1);

    client(second).post(API + "/refresh").then().statusCode(200);

    verify(ai, org.mockito.Mockito.times(2)).analyze(anyList());
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
  void soldStockIsExcludedAndNewHoldingIsAnalyzed() {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    hold(portfolio, "SK하이닉스");
    successfulAi();
    client(member).post(API).then().statusCode(200);
    var sold = holdings.findAllByPortfolioIdOrderById(portfolio.getId()).getLast();
    holdings.delete(sold);
    hold(portfolio, "포스코인터내셔널");
    client(member).get(API).then().statusCode(200).body("matchesCurrentPortfolio", equalTo(false));
    var updated = client(member).post(API);
    updated.then().statusCode(200);
    assertThat(updated.jsonPath().getList("analysis.impacts.assetName", String.class))
        .containsExactlyInAnyOrder("삼성전자", "포스코인터내셔널");
    org.mockito.ArgumentCaptor<List<PortfolioAnalysisTarget>> captor =
        org.mockito.ArgumentCaptor.captor();
    verify(ai, org.mockito.Mockito.times(2)).analyze(captor.capture());
    assertThat(captor.getValue())
        .extracting(PortfolioAnalysisTarget::assetName)
        .containsExactly("포스코인터내셔널");
  }

  @Test
  void quantityChangeWithSameWeightRequiresNewAnalysis() {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    successfulAi();
    var first = client(member).post(API);
    first.then().statusCode(200);
    long firstId = first.jsonPath().getLong("analysisId");
    var holding = holdings.findAllByPortfolioIdOrderById(portfolio.getId()).getFirst();
    holding.change(2, holding.getAveragePurchasePrice());
    holdings.saveAndFlush(holding);
    client(member)
        .get(API)
        .then()
        .statusCode(200)
        .body("matchesCurrentPortfolio", equalTo(false))
        .body("status", equalTo("PORTFOLIO_CHANGED"));
    var next = client(member).post(API);
    next.then().statusCode(200);
    assertThat(next.jsonPath().getLong("analysisId")).isNotEqualTo(firstId);
    var snapshot = analyses.findById(next.jsonPath().getLong("analysisId")).orElseThrow();
    assertThat(snapshot.getSchemaVersion()).isEqualTo(2);
    assertThat(snapshot.getSnapshot().assets().getFirst().quantity()).isEqualTo(2L);
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
        .body("changeReason", equalTo("보유 종목·수량·비중 변경"));
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

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void coreResultsAreAvailableWhileSummaryIsPendingAndRemainIdenticalAfterItCompletes(
      boolean refresh) throws Exception {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    successfulAi();
    if (refresh) {
      client(member).post(API).then().statusCode(200);
      when(ai.refresh(anyList(), anyString()))
          .thenAnswer(
              invocation ->
                  refreshed(
                      invocation.getArgument(0),
                      LocalDate.of(2026, 10, 1),
                      "https://example.invalid/new",
                      "새 계약을 체결했어요."));
    }
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    String text = "현재 보유한 종목의 비중을 확인했어요.\n\n확인된 근거에 따라 영향을 판단했어요.\n\n다음 실적을 확인해 보세요.";
    doAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              started.countDown();
              assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
              return text;
            })
        .when(narrative)
        .summarize(org.mockito.ArgumentMatchers.any());
    Response core;
    try {
      client(member).post(API + (refresh ? "/tasks/refresh" : "/tasks")).then().statusCode(202);
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      core = client(member).get(API);
      core.then()
          .statusCode(200)
          .body("status", equalTo("PROCESSING"))
          .body("summaryStatus", equalTo("PENDING"))
          .body("analysis.overallDirection", equalTo(refresh ? "POSITIVE" : "NEGATIVE"))
          .body("analysis.analyzedAssetCount", equalTo(1));
      assertThat(core.jsonPath().getList("analysis.impacts[0].sources")).isNotEmpty();
      assertThat((Object) core.jsonPath().get("analysis.overallScore")).isNotNull();
      client(member).post(API + "/tasks").then().statusCode(202);
      verify(ai).analyze(anyList());
      verify(narrative, times(refresh ? 2 : 1)).summarize(org.mockito.ArgumentMatchers.any());
    } finally {
      release.countDown();
    }
    awaitTask(member, "READY");
    var completed = client(member).get(API);
    completed
        .then()
        .body("summaryStatus", equalTo("READY"))
        .body("analysis.overallImpact", equalTo(text));
    assertThat(completed.jsonPath().getLong("analysisId"))
        .isEqualTo(core.jsonPath().getLong("analysisId"));
    assertThat(completed.jsonPath().getString("lastSucceededAt"))
        .isEqualTo(core.jsonPath().getString("lastSucceededAt"));
    Map<String, Object> before = new java.util.HashMap<>(core.jsonPath().getMap("analysis"));
    Map<String, Object> after = new java.util.HashMap<>(completed.jsonPath().getMap("analysis"));
    before.remove("overallImpact");
    after.remove("overallImpact");
    assertThat(after).isEqualTo(before);
    client(member)
        .get(API + "/history")
        .then()
        .body("size()", equalTo(refresh ? 2 : 1))
        .body("[0].analysis.overallImpact", equalTo(text));
    var stored = analyses.findById(completed.jsonPath().getLong("analysisId")).orElseThrow();
    assertThat(stored.getSnapshot().overallImpact()).isNotEqualTo(text);
  }

  @Test
  void readingCoreAndTaskStateUsesOneSnapshotWhenSummaryCompletesDuringTheRead() throws Exception {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    var prepared = transactions.prepareTask(member.getId(), false);
    var core =
        transactions.completeCore(
            member.getId(), prepared.token(), prepared.assets(), coreResult());
    var readStarted = new CountDownLatch(1);
    var continueRead = new CountDownLatch(1);
    var load =
        org.mockito.Mockito.mockingDetails(analyses).getMockCreationSettings().getDefaultAnswer();
    doAnswer(
            invocation -> {
              Object existing = load.answer(invocation);
              readStarted.countDown();
              assertThat(continueRead.await(10, TimeUnit.SECONDS)).isTrue();
              return existing;
            })
        .when(analyses)
        .findFirstByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(member.getId());
    var request = client(member);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var reading = executor.submit(() -> request.get(API));
      try {
        assertThat(readStarted.await(5, TimeUnit.SECONDS)).isTrue();
        var completed =
            transactions.completeSummary(
                member.getId(), prepared.token(), core.analysisId(), "종합 설명을 완성했어요.");
        assertThat(completed.summaryStatus()).isEqualTo("READY");
      } finally {
        continueRead.countDown();
      }
      reading
          .get(5, TimeUnit.SECONDS)
          .then()
          .statusCode(200)
          .body("status", equalTo("PROCESSING"))
          .body("summaryStatus", equalTo("PENDING"));
    }
    client(member)
        .get(API)
        .then()
        .body("status", equalTo("READY"))
        .body("summaryStatus", equalTo("READY"));
  }

  @Test
  void expiredSummaryDoesNotStayPendingOrOverwriteTheNextWorkersState() {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    var prepared = transactions.prepareTask(member.getId(), false);
    var core =
        transactions.completeCore(
            member.getId(), prepared.token(), prepared.assets(), coreResult());
    jdbc.update(
        "update portfolio_analysis_checks set request_expires_at = now() - interval '1 second' where portfolio_id = ?",
        portfolio.getId());
    client(member)
        .get(API)
        .then()
        .body("status", equalTo("READY"))
        .body("summaryStatus", equalTo("FAILED"));
    var current = transactions.prepareTask(member.getId(), true);

    assertThatThrownBy(
            () ->
                transactions.completeSummary(
                    member.getId(), prepared.token(), core.analysisId(), "이전 설명이에요."))
        .isInstanceOf(BusinessException.class);
    transactions.failSummary(member.getId(), prepared.token(), core.analysisId());

    assertThat(checks.findById(portfolio.getId()).orElseThrow().getRequestToken())
        .isEqualTo(current.token());
    var stored = analyses.findById(core.analysisId()).orElseThrow();
    assertThat(stored.getOverallImpact()).isEmpty();
    assertThat(stored.getSummaryStatus())
        .isEqualTo(org.grit.daynomy.portfolio.domain.PortfolioAnalysis.SummaryStatus.FAILED);
    verifyNoInteractions(ai, narrative);
  }

  private PortfolioAnalysisResponse coreResult() {
    var impact =
        org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse.of(
            result(List.of(new PortfolioAnalysisTarget("삼성전자"))).impacts().getFirst(),
            new BigDecimal("100"));
    var summary =
        org.grit.daynomy.portfolio.domain.PortfolioImpactSummary.calculate(
            List.of(
                new org.grit.daynomy.portfolio.domain.PortfolioImpactSummary.AssetImpact(
                    impact.weight(), impact.direction(), impact.impactLevel())));
    return PortfolioAnalysisResponse.of(
        1, summary, Instant.now(), "", List.of(impact), impact.sources());
  }

  @Test
  void summaryFailureLeavesCoreResultsAvailableWithoutRepeatingAnalysis() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    successfulAi();
    doAnswer(
            invocation -> {
              throw new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED);
            })
        .when(narrative)
        .summarize(org.mockito.ArgumentMatchers.any());

    client(member).post(API + "/tasks").then().statusCode(202);
    awaitTask(member, "READY");

    var response = client(member).get(API);
    response
        .then()
        .body("summaryStatus", equalTo("FAILED"))
        .body("analysis.analyzedAssetCount", equalTo(1))
        .body("failureCode", nullValue());
    assertThat((Object) response.jsonPath().get("analysis.overallScore")).isNotNull();
    assertThat(response.jsonPath().getList("analysis.impacts[0].sources")).isNotEmpty();
    client(member)
        .post(API + "/tasks")
        .then()
        .statusCode(200)
        .body("summaryStatus", equalTo("FAILED"));
    verify(ai).analyze(anyList());
    verify(narrative).summarize(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void backgroundAnalysisReturnsBeforeAiCompletesAndRepeatedStartsReuseIt() throws Exception {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(ai.analyze(anyList()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              started.countDown();
              assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
              return result(invocation.getArgument(0));
            });
    try {
      client(member)
          .post(API + "/tasks")
          .then()
          .statusCode(202)
          .header("Location", API)
          .body("status", equalTo("PROCESSING"));
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
      UUID token = checks.findById(portfolio.getId()).orElseThrow().getRequestToken();
      try (var requests = Executors.newFixedThreadPool(4)) {
        var repeated = new java.util.ArrayList<java.util.concurrent.Future<Response>>();
        for (int index = 0; index < 4; index++) {
          var client = client(member);
          repeated.add(requests.submit(() -> client.post(API + "/tasks")));
        }
        for (var response : repeated) {
          response.get(5, TimeUnit.SECONDS).then().statusCode(202);
        }
      }
      client(member).get(API).then().statusCode(200).body("status", equalTo("PROCESSING"));
      client(member).post(API + "/tasks/refresh").then().statusCode(202);
      assertThat(checks.findById(portfolio.getId()).orElseThrow().getRequestToken())
          .isEqualTo(token);
      // 다른 회원의 조회에 진행 상태나 분석이 노출되지 않는다.
      client(member()).get(API).then().statusCode(200).body("status", equalTo("EMPTY"));
      verify(ai).analyze(anyList());
    } finally {
      release.countDown();
    }
    awaitTask(member, "READY");
    long id = client(member).get(API).jsonPath().getLong("analysisId");
    client(member)
        .post(API + "/tasks")
        .then()
        .statusCode(200)
        .body("analysisId", equalTo((int) id));
    verify(ai).analyze(anyList());
  }

  @Test
  void failedBackgroundRefreshPreservesStoredResultAndCanBeRetried() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    successfulAi();
    long id = client(member).post(API).jsonPath().getLong("analysisId");
    when(ai.refresh(anyList(), anyString()))
        .thenThrow(new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED));

    client(member).post(API + "/tasks/refresh").then().statusCode(202);
    awaitTask(member, "FAILED");
    client(member)
        .get(API)
        .then()
        .statusCode(200)
        .body("analysisId", equalTo((int) id))
        .body("failureCode", equalTo("AI_PORTFOLIO_ANALYSIS_FAILED"));

    doAnswer(invocation -> result(invocation.getArgument(0)))
        .when(ai)
        .refresh(anyList(), anyString());
    client(member).post(API + "/tasks/refresh").then().statusCode(202);
    awaitTask(member, "READY");
    verify(ai, times(2)).refresh(anyList(), anyString());
    verify(narrative).summarize(org.mockito.ArgumentMatchers.any());
  }

  private void awaitTask(Member member, String status) {
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () -> client(member).get(API).then().statusCode(200).body("status", equalTo(status)));
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
  void guestStartRequiresBootstrapAndInvalidOrEmptyInputDoesNotConsumeTrial() {
    var body = Map.of("assets", List.of(Map.of("assetName", "삼성전자", "weight", 100)));
    given()
        .port(port)
        .contentType("application/json")
        .body(body)
        .post(GUEST)
        .then()
        .statusCode(409)
        .body("code", equalTo("PORTFOLIO_ANALYSIS_SESSION_REQUIRED"));
    given()
        .port(port)
        .contentType("application/json")
        .body(Map.of("assets", List.of()))
        .post(GUEST)
        .then()
        .statusCode(200)
        .body("taskId", nullValue());
    var bootstrap = given().port(port).get(GUEST);
    bootstrap.then().statusCode(200).body("status", equalTo("EMPTY"));
    assertThat(bootstrap.cookie("JSESSIONID")).isNotBlank();
    given()
        .port(port)
        .cookies(bootstrap.cookies())
        .contentType("application/json")
        .body(Map.of("assets", List.of(Map.of("assetName", "삼성전자", "weight", 90))))
        .post(GUEST)
        .then()
        .statusCode(400)
        .body("code", equalTo("INVALID_PORTFOLIO_WEIGHT_TOTAL"));
    given()
        .port(port)
        .cookies(bootstrap.cookies())
        .get(GUEST)
        .then()
        .body("status", equalTo("EMPTY"));
    verifyNoInteractions(ai, narrative);
  }

  @Test
  void guestBackgroundTaskIsRecoveredBySessionAndConcurrentResubmissionDoesNotRepeatAi()
      throws Exception {
    var cookies = given().port(port).get(GUEST).cookies();
    var body = Map.of("assets", List.of(Map.of("assetName", "삼성전자", "weight", 100)));
    var aiStarted = new CountDownLatch(1);
    var releaseAi = new CountDownLatch(1);
    var summaryStarted = new CountDownLatch(1);
    var releaseSummary = new CountDownLatch(1);
    when(ai.analyze(anyList()))
        .thenAnswer(
            invocation -> {
              aiStarted.countDown();
              assertThat(releaseAi.await(20, TimeUnit.SECONDS)).isTrue();
              return result(invocation.getArgument(0));
            });
    doAnswer(
            invocation -> {
              summaryStarted.countDown();
              assertThat(releaseSummary.await(20, TimeUnit.SECONDS)).isTrue();
              return "현재 보유한 종목의 비중을 확인했어요.\n\n실적 감소가 부담이 될 수 있어요.\n\n다음 발표를 확인해 보세요.";
            })
        .when(narrative)
        .summarize(org.mockito.ArgumentMatchers.any());
    String id;
    try {
      var accepted =
          given()
              .port(port)
              .cookies(cookies)
              .contentType("application/json")
              .body(body)
              .post(GUEST);
      accepted.then().statusCode(202);
      id = accepted.jsonPath().getString("taskId");
      assertThat(aiStarted.await(5, TimeUnit.SECONDS)).isTrue();
      given()
          .port(port)
          .cookies(cookies)
          .get(GUEST)
          .then()
          .body("taskId", equalTo(id))
          .body("status", equalTo("PROCESSING"));
      try (var requests = Executors.newFixedThreadPool(4)) {
        var repeated = new java.util.ArrayList<java.util.concurrent.Future<Response>>();
        for (int index = 0; index < 4; index++) {
          repeated.add(
              requests.submit(
                  () ->
                      given()
                          .port(port)
                          .cookies(cookies)
                          .contentType("application/json")
                          .body(body)
                          .post(GUEST)));
        }
        for (var response : repeated) {
          response.get(5, TimeUnit.SECONDS).then().statusCode(202).body("taskId", equalTo(id));
        }
      }
      given()
          .port(port)
          .cookies(cookies)
          .contentType("application/json")
          .body(body)
          .post(LEGACY)
          .then()
          .statusCode(403);
      given()
          .port(port)
          .cookies(cookies)
          .contentType("application/json")
          .body(Map.of("assets", List.of(Map.of("assetName", "다른 종목", "weight", 100))))
          .post(GUEST)
          .then()
          .statusCode(403);
      var otherCookies = given().port(port).get(GUEST).cookies();
      given()
          .port(port)
          .cookies(otherCookies)
          .get(GUEST + "/" + id)
          .then()
          .statusCode(404)
          .body("code", equalTo("PORTFOLIO_ANALYSIS_TASK_NOT_FOUND"));
      releaseAi.countDown();
      assertThat(summaryStarted.await(5, TimeUnit.SECONDS)).isTrue();
      given()
          .port(port)
          .cookies(cookies)
          .get(GUEST + "/" + id)
          .then()
          .body("status", equalTo("PROCESSING"))
          .body("summaryStatus", equalTo("PENDING"))
          .body("analysis.analyzedAssetCount", equalTo(1));
    } finally {
      releaseAi.countDown();
      releaseSummary.countDown();
    }
    awaitGuest(cookies, "READY");
    given()
        .port(port)
        .cookies(cookies)
        .contentType("application/json")
        .body(body)
        .post(GUEST)
        .then()
        .statusCode(200)
        .body("taskId", equalTo(id))
        .body("summaryStatus", equalTo("READY"));
    given().port(port).cookies(cookies).post(GUEST + "/" + id + "/retry").then().statusCode(403);
    verify(ai).analyze(anyList());
    verify(narrative).summarize(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void guestFailureDoesNotAutomaticallyRetryButExplicitRetryUsesTheOriginalInput() {
    var cookies = given().port(port).get(GUEST).cookies();
    var body = Map.of("assets", List.of(Map.of("assetName", "삼성전자", "weight", 100)));
    when(ai.analyze(anyList()))
        .thenThrow(new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED))
        .thenAnswer(invocation -> result(invocation.getArgument(0)));
    var accepted =
        given().port(port).cookies(cookies).contentType("application/json").body(body).post(GUEST);
    accepted.then().statusCode(202);
    String id = accepted.jsonPath().getString("taskId");
    awaitGuest(cookies, "FAILED");
    given()
        .port(port)
        .cookies(cookies)
        .contentType("application/json")
        .body(body)
        .post(GUEST)
        .then()
        .statusCode(200)
        .body("status", equalTo("FAILED"))
        .body("retryAllowed", equalTo(true));
    verify(ai).analyze(anyList());

    given().port(port).cookies(cookies).post(GUEST + "/" + id + "/retry").then().statusCode(202);
    awaitGuest(cookies, "READY");
    given()
        .port(port)
        .cookies(cookies)
        .get(GUEST + "/" + id)
        .then()
        .body("analysis.impacts[0].assetName", equalTo("삼성전자"));
    verify(ai, times(2)).analyze(anyList());
    verify(narrative).summarize(org.mockito.ArgumentMatchers.any());
  }

  @Test
  void authenticatedMembersCannotUseGuestStartOrRetryEvenWithValidCsrfAndSession() {
    var cookies = given().port(port).get(GUEST).cookies();
    Member member = member();
    client(member)
        .cookies(cookies)
        .body(Map.of("assets", List.of(Map.of("assetName", "삼성전자", "weight", 100))))
        .post(GUEST)
        .then()
        .statusCode(403);
    client(member)
        .cookies(cookies)
        .post(GUEST + "/" + UUID.randomUUID() + "/retry")
        .then()
        .statusCode(403);
    verifyNoInteractions(ai, narrative);
  }

  private void awaitGuest(Map<String, String> cookies, String status) {
    org.awaitility.Awaitility.await()
        .atMost(java.time.Duration.ofSeconds(10))
        .untilAsserted(
            () ->
                given()
                    .port(port)
                    .cookies(cookies)
                    .get(GUEST)
                    .then()
                    .statusCode(200)
                    .body("status", equalTo(status)));
  }

  @Test
  void backgroundEndpointsRequireAuthenticationAndCsrf() {
    Member member = member();
    for (String path : List.of(API + "/tasks", API + "/tasks/refresh")) {
      var csrf = given().port(port).get("/api/auth/csrf");
      given()
          .port(port)
          .cookies(csrf.cookies())
          .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"))
          .post(path)
          .then()
          .statusCode(401);
      given()
          .port(port)
          .cookie("access_token", accessToken(member))
          .post(path)
          .then()
          .statusCode(403);
    }
    verifyNoInteractions(ai, narrative);
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

  @Test
  void persistsInsufficientEvidenceWithoutNeutralScoreAndReportsCoverage() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    when(ai.analyze(anyList()))
        .thenReturn(
            new PortfolioAnalysisResult(
                "판단 근거를 찾지 못했어요.",
                List.of(
                    new PortfolioAnalysisResult.AssetImpactResult(
                        "삼성전자",
                        null,
                        null,
                        "판단 근거를 찾지 못했어요.",
                        "",
                        "",
                        "근거 부족",
                        "근거 부족",
                        List.of(),
                        1)),
                List.of()));
    client(member).post(API).then().statusCode(200);
    client(member)
        .get(API)
        .then()
        .statusCode(200)
        .body("status", equalTo("RECHECK_REQUIRED"))
        .body("lastCheckOutcome", equalTo("UNCONFIRMED"))
        .body("analysis.coverage", equalTo("INSUFFICIENT"))
        .body("analysis.analyzedAssetCount", equalTo(0))
        .body("analysis.analyzedWeight", equalTo(0))
        .body("analysis.overallDirection", nullValue())
        .body("analysis.overallScore", nullValue())
        .body("analysis.impacts[0].evidenceStatus", equalTo("INSUFFICIENT"))
        .body("analysis.impacts[0].direction", nullValue());
    when(ai.refresh(anyList(), anyString()))
        .thenAnswer(invocation -> ai.analyze(invocation.getArgument(0)));
    client(member)
        .post(API + "/refresh")
        .then()
        .statusCode(200)
        .body("status", equalTo("RECHECK_REQUIRED"))
        .body("lastCheckOutcome", equalTo("UNCONFIRMED"))
        .body("analysis.analyzedAssetCount", equalTo(0));
    client(member).get(API + "/history").then().body("size()", equalTo(1));
  }

  @Test
  void refreshWithSameFactPreservesOriginalJudgmentAndCreationTime() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    successfulAi();
    var first = client(member).post(API);
    long id = first.jsonPath().getLong("analysisId");
    String analyzedAt = first.jsonPath().getString("analysis.analyzedAt");
    when(ai.refresh(anyList(), anyString()))
        .thenAnswer(
            invocation ->
                refreshed(
                    invocation.getArgument(0),
                    LocalDate.of(2026, 9, 25),
                    "https://example.invalid/reprint",
                    "영업이익이 감소했어요."));
    client(member)
        .post(API + "/refresh")
        .then()
        .statusCode(200)
        .body("analysisId", equalTo((int) id))
        .body("analysis.analyzedAt", equalTo(analyzedAt))
        .body("analysis.impacts[0].direction", equalTo("NEGATIVE"));
    client(member).get(API + "/history").then().statusCode(200).body("size()", equalTo(1));
    verify(ai).refresh(anyList(), anyString());
  }

  @Test
  void datedNewEvidenceCreatesLinkedRevisionAndIsolatesHistory() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    successfulAi();
    long oldId = client(member).post(API).jsonPath().getLong("analysisId");
    when(ai.refresh(anyList(), anyString()))
        .thenAnswer(
            invocation ->
                refreshed(
                    invocation.getArgument(0),
                    LocalDate.of(2026, 10, 1),
                    "https://example.invalid/new",
                    "새 계약을 체결했어요."));
    client(member)
        .post(API + "/refresh")
        .then()
        .statusCode(200)
        .body("analysis.impacts[0].direction", equalTo("POSITIVE"));
    var history = client(member).get(API + "/history");
    history
        .then()
        .statusCode(200)
        .body("size()", equalTo(2))
        .body("[0].previousAnalysisId", equalTo((int) oldId))
        .body("[1].analysis.impacts[0].direction", equalTo("NEGATIVE"));
    assertThat(history.jsonPath().getString("[0].changeReason")).isEqualTo("삼성전자의 근거와 설명을 갱신했어요.");
    client(member()).get(API + "/history").then().statusCode(200).body("size()", equalTo(0));
    given().port(port).get(API + "/history").then().statusCode(401);
  }

  @Test
  void refreshWithoutConfirmedEvidenceKeepsResultAndRequiresRecheck() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    successfulAi();
    long id = client(member).post(API).jsonPath().getLong("analysisId");
    when(ai.refresh(anyList(), anyString()))
        .thenReturn(
            new PortfolioAnalysisResult(
                "근거를 확인하지 못했어요.",
                List.of(
                    new PortfolioAnalysisResult.AssetImpactResult(
                        "삼성전자",
                        null,
                        null,
                        "근거를 확인하지 못했어요.",
                        "",
                        "",
                        "근거가 부족해요.",
                        "근거가 부족해요.",
                        List.of(),
                        1)),
                List.of()));
    client(member)
        .post(API + "/refresh")
        .then()
        .statusCode(200)
        .body("status", equalTo("RECHECK_REQUIRED"))
        .body("analysisId", equalTo((int) id));
    client(member).get(API).then().statusCode(200).body("status", equalTo("RECHECK_REQUIRED"));
    client(member).get(API + "/history").then().body("size()", equalTo(1));
  }

  @Test
  void expiredCheckNeedsRefreshAndFailurePreservesPreviousSuccessfulTime() {
    Member member = member();
    Portfolio portfolio = portfolio(member);
    hold(portfolio, "삼성전자");
    successfulAi();
    long id = client(member).post(API).jsonPath().getLong("analysisId");
    jdbc.update(
        "update portfolio_analysis_checks set last_succeeded_at = now() - interval '25 hours' where portfolio_id = ?",
        portfolio.getId());
    var stored = client(member).get(API);
    stored.then().statusCode(200).body("status", equalTo("RECHECK_REQUIRED"));
    String lastSucceededAt = stored.jsonPath().getString("lastSucceededAt");
    when(ai.refresh(anyList(), anyString()))
        .thenThrow(new BusinessException(ExternalErrorCode.AI_PORTFOLIO_ANALYSIS_FAILED));
    client(member).post(API + "/refresh").then().statusCode(502);
    client(member)
        .get(API)
        .then()
        .statusCode(200)
        .body("status", equalTo("FAILED"))
        .body("lastSucceededAt", equalTo(lastSucceededAt))
        .body("analysisId", equalTo((int) id));
    doAnswer(invocation -> result(invocation.getArgument(0)))
        .when(ai)
        .refresh(anyList(), anyString());
    client(member)
        .post(API + "/refresh")
        .then()
        .statusCode(200)
        .body("status", equalTo("READY"))
        .body("analysisId", equalTo((int) id));
  }

  @Test
  void concurrentRefreshRunsOnceAndSearchDoesNotHoldTransaction() throws Exception {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    successfulAi();
    client(member).post(API).then().statusCode(200);
    var started = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    when(ai.refresh(anyList(), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
              started.countDown();
              assertThat(release.await(20, TimeUnit.SECONDS)).isTrue();
              return result(invocation.getArgument(0));
            });
    RequestSpecification first = client(member);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var pending = executor.submit(() -> first.post(API + "/refresh"));
      try {
        assertThat(started.await(15, TimeUnit.SECONDS)).isTrue();
        client(member).get(API).then().statusCode(200).body("status", equalTo("PROCESSING"));
        client(member).post(API + "/refresh").then().statusCode(409);
      } finally {
        release.countDown();
      }
      pending.get(20, TimeUnit.SECONDS).then().statusCode(200);
    }
    verify(ai).refresh(anyList(), anyString());
    client(member).get(API + "/history").then().body("size()", equalTo(1));
  }

  @Test
  void restoresQuotesAndKeepsJudgmentWhenOnlySummaryChanges() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    when(ai.analyze(anyList()))
        .thenAnswer(
            invocation ->
                quotedResult(invocation.getArgument(0), "기존 요약이에요.", "Operating profit fell 10%."));
    long id = client(member).post(API).jsonPath().getLong("analysisId");
    client(member)
        .get(API)
        .then()
        .statusCode(200)
        .body("analysis.impacts[0].sources[0].factQuote", equalTo("Operating profit fell 10%."))
        .body(
            "analysis.sources.find { it.url == 'https://example.invalid/overall' }.factQuote",
            equalTo("Industry demand declined."));
    when(ai.refresh(anyList(), anyString()))
        .thenAnswer(
            invocation -> {
              assertThat((String) invocation.getArgument(1)).contains("Operating profit fell 10%.");
              return quotedResult(
                  invocation.getArgument(0), "표현만 달라진 요약이에요.", "Operating profit fell 10%.");
            });
    client(member)
        .post(API + "/refresh")
        .then()
        .statusCode(200)
        .body("analysisId", equalTo((int) id))
        .body("status", equalTo("READY"))
        .body("lastCheckOutcome", equalTo("UNCHANGED"));
  }

  @Test
  void correctedQuoteWithSameSummaryRequiresRecheckAfterDatabaseRestore() {
    Member member = member();
    hold(portfolio(member), "삼성전자");
    when(ai.analyze(anyList()))
        .thenAnswer(
            invocation ->
                quotedResult(
                    invocation.getArgument(0), "이익이 감소했어요.", "Operating profit fell 10%."));
    long id = client(member).post(API).jsonPath().getLong("analysisId");
    when(ai.refresh(anyList(), anyString()))
        .thenAnswer(
            invocation ->
                quotedResult(
                    invocation.getArgument(0), "이익이 감소했어요.", "Operating profit fell 20%."));
    client(member)
        .post(API + "/refresh")
        .then()
        .statusCode(200)
        .body("analysisId", equalTo((int) id))
        .body("status", equalTo("RECHECK_REQUIRED"))
        .body("lastCheckOutcome", equalTo("UNCONFIRMED"));
    client(member)
        .get(API)
        .then()
        .statusCode(200)
        .body("status", equalTo("RECHECK_REQUIRED"))
        .body("analysis.impacts[0].sources[0].factQuote", equalTo("Operating profit fell 10%."));
  }

  private PortfolioAnalysisResult quotedResult(
      List<PortfolioAnalysisTarget> targets, String excerpt, String quote) {
    var source =
        new PortfolioAnalysisResult.Source(
            "실적 발표",
            "https://example.invalid/evidence",
            LocalDate.of(2026, 9, 25),
            LocalDate.of(2026, 9, 25),
            excerpt,
            quote);
    var original = result(targets);
    var impacts =
        original.impacts().stream()
            .map(
                impact ->
                    new PortfolioAnalysisResult.AssetImpactResult(
                        impact.assetName(),
                        impact.direction(),
                        impact.impactLevel(),
                        impact.issueSummary(),
                        impact.expectedReaction(),
                        impact.outlook(),
                        impact.reason(),
                        impact.evidenceSentence(),
                        List.of(source),
                        impact.sortOrder()))
            .toList();
    return new PortfolioAnalysisResult(
        original.overallImpact(),
        impacts,
        List.of(
            source,
            new PortfolioAnalysisResult.Source(
                "산업 발표",
                "https://example.invalid/overall",
                LocalDate.of(2026, 9, 25),
                LocalDate.of(2026, 9, 25),
                "산업 수요가 줄었어요.",
                "Industry demand declined.")));
  }

  private PortfolioAnalysisResult refreshed(
      List<PortfolioAnalysisTarget> targets, LocalDate occurredOn, String url, String fact) {
    var source = new PortfolioAnalysisResult.Source("공식 발표", url, occurredOn, occurredOn, fact);
    var impacts =
        targets.stream()
            .map(
                target ->
                    new PortfolioAnalysisResult.AssetImpactResult(
                        target.assetName(),
                        ImpactDirection.POSITIVE,
                        ImpactLevel.HIGH,
                        fact,
                        "실적 개선을 기대할 수 있어요.",
                        "다음 실적을 확인해 보세요.",
                        fact,
                        fact,
                        List.of(source),
                        1))
            .toList();
    return new PortfolioAnalysisResult("새 발표가 있어요.", impacts, List.of(source));
  }

  private void successfulAi() {
    doAnswer(invocation -> result(invocation.getArgument(0))).when(ai).analyze(anyList());
  }

  private PortfolioAnalysisResult result(List<PortfolioAnalysisTarget> targets) {
    var source =
        new PortfolioAnalysisResult.Source(
            "실적 발표",
            "https://example.invalid/evidence",
            LocalDate.of(2026, 9, 29),
            LocalDate.of(2026, 9, 25),
            "영업이익이 감소했어요.");
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
