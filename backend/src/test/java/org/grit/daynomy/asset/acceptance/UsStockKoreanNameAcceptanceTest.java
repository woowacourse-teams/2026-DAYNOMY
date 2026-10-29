package org.grit.daynomy.asset.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.archive;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.row;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.time.LocalDate;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberRole;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsStockKoreanNameAcceptanceTest {
  private static final String SYNC_URL = "/api/admin/stocks/us/korean-names/sync";

  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  private static final MockWebServer KIS = startServer();

  private static MockWebServer startServer() {
    try {
      MockWebServer server = new MockWebServer();
      server.start();
      return server;
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @AfterAll
  static void close() throws IOException {
    KIS.shutdown();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
    registry.add("external.kis.base-url", () -> KIS.url("/master").toString());
  }

  @LocalServerPort int port;
  @Autowired AssetRepository assets;
  @Autowired MemberRepository members;
  @Autowired JwtTokenProvider tokens;
  @Autowired PlatformTransactionManager transactions;

  @Test
  void adminDownloadToDatabaseIsIdempotentAndFailedLastFilePreservesNames() {
    LocalDate date = LocalDate.of(2026, 10, 27);
    Asset apple =
        assets.saveAndFlush(Asset.listedStock("Apple", "AAPL.US", StockMarket.NASDAQ, null, date));
    Asset korean =
        assets.saveAndFlush(Asset.listedStock("삼성전자", "005930", StockMarket.KOSPI, null, date));
    Asset etf =
        assets.saveAndFlush(
            Asset.listedSecurity(
                "Vanguard", AssetCategory.ETF, "VTI.US", StockMarket.NYSE_ARCA, null, date));
    Asset missing = Asset.listedStock("Missing", "MISS.US", StockMarket.NASDAQ, null, date);
    missing.synchronizeKoreanName("기존 한글명");
    missing = assets.saveAndFlush(missing);
    long assetCount = assets.count();
    Member member =
        members.saveAndFlush(
            Member.createGoogleMember("kis-test", "kis@example.invalid", "kis-test", null));

    given().port(port).post(SYNC_URL).then().statusCode(403);
    client(member, MemberRole.USER).post(SYNC_URL).then().statusCode(403);
    given()
        .port(port)
        .cookie(
            "access_token", tokens.createTokenPair(member.getId(), MemberRole.ADMIN).accessToken())
        .post(SYNC_URL)
        .then()
        .statusCode(403);
    assertThat(KIS.getRequestCount()).isZero();

    enqueueFiles("애플");
    client(member, MemberRole.ADMIN)
        .post(SYNC_URL)
        .then()
        .statusCode(200)
        .body("downloadedCount", equalTo(4))
        .body("matchedCount", equalTo(2))
        .body("updatedCount", equalTo(2))
        .body("unmatchedCount", equalTo(1))
        .body("skippedCount", equalTo(1));
    assertThat(assets.findById(apple.getId()).orElseThrow().getNameKo()).isEqualTo("애플");
    assertThat(assets.findById(apple.getId()).orElseThrow().getName()).isEqualTo("Apple");
    assertThat(assets.findById(etf.getId()).orElseThrow().getNameKo()).isEqualTo("뱅가드 ETF");
    assertThat(assets.findById(korean.getId()).orElseThrow().getNameKo()).isNull();
    assertThat(assets.findById(missing.getId()).orElseThrow().getNameKo()).isEqualTo("기존 한글명");
    assertThat(assets.count()).isEqualTo(assetCount);

    enqueueFiles("애플");
    client(member, MemberRole.ADMIN)
        .post(SYNC_URL)
        .then()
        .statusCode(200)
        .body("updatedCount", equalTo(0));

    zip("NASMST.COD", row("NAS", "AAPL", "실패하면 저장 금지", "2"));
    zip("NYSMST.COD", row("NYS", "IBM", "아이비엠", "2"));
    KIS.enqueue(new MockResponse().setResponseCode(404));
    client(member, MemberRole.ADMIN)
        .post(SYNC_URL)
        .then()
        .statusCode(502)
        .body("code", equalTo("KIS_STOCK_MASTER_REQUEST_FAILED"));
    assertThat(assets.findById(apple.getId()).orElseThrow().getNameKo()).isEqualTo("애플");
    assertThat(assets.findById(etf.getId()).orElseThrow().getNameKo()).isEqualTo("뱅가드 ETF");
  }

  @Test
  void overlappingMasterUpdateDoesNotOverwriteNewKoreanName() {
    LocalDate date = LocalDate.of(2026, 10, 27);
    Asset original =
        assets.saveAndFlush(
            Asset.listedStock("Old name", "DYNAMIC.US", StockMarket.NASDAQ, null, date));
    TransactionTemplate master = new TransactionTemplate(transactions);
    TransactionTemplate koreanNames = new TransactionTemplate(transactions);
    koreanNames.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

    master.executeWithoutResult(
        status -> {
          Asset stale = assets.findById(original.getId()).orElseThrow();
          koreanNames.executeWithoutResult(
              inner ->
                  assets.findById(original.getId()).orElseThrow().synchronizeKoreanName("새 한글명"));
          stale.synchronizeStock("New name", StockMarket.NASDAQ, null, date);
        });

    Asset result = assets.findById(original.getId()).orElseThrow();
    assertThat(result.getName()).isEqualTo("New name");
    assertThat(result.getNameKo()).isEqualTo("새 한글명");
  }

  private void enqueueFiles(String appleName) {
    zip("NASMST.COD", row("NAS", "AAPL", appleName, "2") + "\n" + row("NAS", "MSFT", "", "2"));
    zip("NYSMST.COD", row("NYS", "IBM", "아이비엠", "2"));
    zip("AMSMST.COD", row("AMS", "VTI", "뱅가드 ETF", "3"));
  }

  private void zip(String filename, String content) {
    KIS.enqueue(new MockResponse().setBody(archive(filename, content)));
  }

  private RequestSpecification client(Member member, MemberRole role) {
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .contentType("application/json")
        .cookies(csrf.cookies())
        .cookie("access_token", tokens.createTokenPair(member.getId(), role).accessToken())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }
}
