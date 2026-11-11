package org.grit.daynomy.asset.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.archive;
import static org.grit.daynomy.external.kis.KisStockMasterFixtures.row;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.asset.domain.AssetCategory;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class UsStockSyncWorkflowAcceptanceTest {
  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  private static final MockWebServer EODHD = server();
  private static final MockWebServer KIS = server();
  private static final String SYNC = "/api/admin/stocks/us/sync";

  private static MockWebServer server() {
    try {
      var server = new MockWebServer();
      server.start();
      return server;
    } catch (IOException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @AfterAll
  static void close() throws IOException {
    EODHD.shutdown();
    KIS.shutdown();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
    registry.add("external.eodhd.base-url", () -> EODHD.url("/api").toString());
    registry.add("external.eodhd.api-token", () -> "test-token");
    registry.add("external.kis.base-url", () -> KIS.url("/master").toString());
  }

  @LocalServerPort int port;
  @Autowired AssetRepository assets;
  @Autowired MemberRepository members;
  @Autowired JwtTokenProvider tokens;
  @Autowired JdbcTemplate jdbc;

  @Test
  void retriesFreshDatabaseTransactionsWithoutRedownloadingAndPreservesSuccessfulStage()
      throws InterruptedException {
    Member admin =
        members.saveAndFlush(
            Member.createGoogleMember(
                "sync-workflow", "workflow@example.invalid", "workflow", null));
    // Sequences survive rollbacks. Fail the second write on each stage's first transaction.
    jdbc.execute("CREATE SEQUENCE master_attempt");
    jdbc.execute("CREATE SEQUENCE names_attempt");
    jdbc.execute(
        """
        CREATE FUNCTION fail_first_stock_write() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.asset_code = 'RETRY.US' AND nextval('master_attempt') = 1 THEN
            RAISE EXCEPTION 'temporary master write' USING ERRCODE = '40001';
          END IF;
          RETURN NEW;
        END $$
        """);
    jdbc.execute(
        "CREATE TRIGGER retry_master BEFORE INSERT ON assets FOR EACH ROW EXECUTE FUNCTION fail_first_stock_write()");
    jdbc.execute(
        """
        CREATE FUNCTION fail_first_name_write() RETURNS trigger LANGUAGE plpgsql AS $$
        BEGIN
          IF NEW.asset_code = 'RETRY.US' AND nextval('names_attempt') = 1 THEN
            RAISE EXCEPTION 'temporary name write' USING ERRCODE = '40001';
          END IF;
          RETURN NEW;
        END $$
        """);
    jdbc.execute(
        "CREATE TRIGGER retry_names BEFORE UPDATE OF name_ko ON assets FOR EACH ROW EXECUTE FUNCTION fail_first_name_write()");

    master("Original");
    names("재시도 종목", true);
    client(admin)
        .post(SYNC)
        .then()
        .statusCode(200)
        .body("createdCount", equalTo(2))
        .body("koreanNames.updatedCount", equalTo(2))
        .body("koreanNameErrorCode", nullValue());
    assertThat(EODHD.getRequestCount()).isEqualTo(1);
    assertThat(KIS.getRequestCount()).isEqualTo(4);
    for (String market : new String[] {"nas", "nys", "nys", "ams"}) {
      assertThat(KIS.takeRequest().getRequestUrl().encodedPath())
          .isEqualTo("/master/" + market + "mst.cod.zip");
    }
    assertThat(jdbc.queryForObject("SELECT last_value FROM master_attempt", Long.class))
        .isEqualTo(2);
    assertThat(jdbc.queryForObject("SELECT last_value FROM names_attempt", Long.class))
        .isEqualTo(2);
    assertThat(
            assets
                .findByCategoryAndAssetCode(AssetCategory.STOCK, "RETRY.US")
                .orElseThrow()
                .getNameKo())
        .isEqualTo("재시도 종목");
    assertThat(
            assets
                .findByCategoryAndAssetCode(AssetCategory.ETF, "RTRYETF.US")
                .orElseThrow()
                .getNameKo())
        .isEqualTo("재시도 ETF");
    jdbc.execute("DROP TRIGGER retry_master ON assets");
    jdbc.execute("DROP TRIGGER retry_names ON assets");

    master("Updated");
    KIS.enqueue(new MockResponse().setResponseCode(404));
    client(admin)
        .post(SYNC)
        .then()
        .statusCode(200)
        .body("koreanNames", nullValue())
        .body("koreanNameErrorCode", equalTo("KIS_STOCK_MASTER_REQUEST_FAILED"));
    var stock = assets.findByCategoryAndAssetCode(AssetCategory.STOCK, "RETRY.US").orElseThrow();
    assertThat(stock.getName()).isEqualTo("Updated");
    assertThat(stock.getNameKo()).isEqualTo("재시도 종목");

    names("새 한글명");
    client(admin)
        .post("/api/admin/stocks/us/korean-names/sync")
        .then()
        .statusCode(200)
        .body("updatedCount", equalTo(1));
    assertThat(EODHD.getRequestCount()).isEqualTo(2);
    assertThat(KIS.getRequestCount()).isEqualTo(8);
    assertThat(assets.count()).isEqualTo(2);

    for (int attempt = 0; attempt < 3; attempt++)
      EODHD.enqueue(new MockResponse().setResponseCode(503));
    client(admin)
        .post(SYNC)
        .then()
        .statusCode(502)
        .body("code", equalTo("EODHD_API_REQUEST_FAILED"));
    assertThat(EODHD.getRequestCount()).isEqualTo(5);
    assertThat(KIS.getRequestCount()).isEqualTo(8);
    assertThat(
            assets
                .findByCategoryAndAssetCode(AssetCategory.STOCK, "RETRY.US")
                .orElseThrow()
                .getNameKo())
        .isEqualTo("새 한글명");
  }

  private void master(String name) {
    EODHD.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody(
                """
        [{"Code":"RTRYETF","Name":"Retry ETF","Exchange":"NASDAQ","Currency":"USD","Type":"ETF"},
         {"Code":"RETRY","Name":"%s","Exchange":"NASDAQ","Currency":"USD","Type":"Common Stock"}]
        """
                    .formatted(name)));
  }

  private void names(String name) {
    names(name, false);
  }

  private void names(String name, boolean retryNys) {
    KIS.enqueue(
        new MockResponse()
            .setBody(
                archive(
                    "NASMST.COD",
                    row("NAS", "RTRYETF", "재시도 ETF", "3")
                        + "\n"
                        + row("NAS", "RETRY", name, "2"))));
    if (retryNys) KIS.enqueue(new MockResponse().setResponseCode(503));
    KIS.enqueue(new MockResponse().setBody(archive("NYSMST.COD", row("NYS", "IBM", "아이비엠", "2"))));
    KIS.enqueue(
        new MockResponse().setBody(archive("AMSMST.COD", row("AMS", "SPY", "에스피 ETF", "3"))));
  }

  private RequestSpecification client(Member member) {
    jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", member.getId());
    var csrf = given().port(port).get("/api/auth/csrf");
    return given()
        .port(port)
        .cookies(csrf.cookies())
        .cookie(
            "access_token", tokens.createTokenPair(member.getId(), MemberRole.ADMIN).accessToken())
        .header(csrf.jsonPath().getString("headerName"), csrf.jsonPath().getString("token"));
  }
}
