package org.grit.daynomy.asset.acceptance;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

import io.restassured.specification.RequestSpecification;
import java.io.IOException;
import java.time.LocalDate;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.asset.domain.*;
import org.grit.daynomy.asset.repository.*;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.*;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
      "stock.us.sync.enabled=false",
      "stock.sync.enabled=false",
      "stock.price.sync.enabled=false"
    })
class UsStockAcceptanceTest {
  @Container
  static final PostgreSQLContainer POSTGRESQL =
      new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));

  static final MockWebServer EODHD = startServer();

  private static MockWebServer startServer() {
    try {
      var server = new MockWebServer();
      server.start();
      return server;
    } catch (IOException e) {
      throw new IllegalStateException(e);
    }
  }

  @AfterAll
  static void close() throws IOException {
    EODHD.shutdown();
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRESQL::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRESQL::getUsername);
    registry.add("spring.datasource.password", POSTGRESQL::getPassword);
    registry.add("spring.datasource.driver-class-name", POSTGRESQL::getDriverClassName);
    registry.add("external.eodhd.base-url", () -> EODHD.url("/api").toString());
    registry.add("external.eodhd.api-token", () -> "test-token");
  }

  @LocalServerPort int port;
  @Autowired AssetRepository assets;
  @Autowired StockDailyPriceRepository prices;
  @Autowired MarketTradingDayRepository calendar;
  @Autowired MemberRepository members;
  @Autowired JwtTokenProvider tokens;

  @Test
  void adminSyncToPublicSearchAndUsdPricePreservesDomesticData() {
    LocalDate date = LocalDate.of(2020, 10, 16);
    Asset korean =
        assets.saveAndFlush(
            Asset.listedStock("Apple Korea", "123456", StockMarket.KOSPI, null, date));
    calendar.saveAndFlush(new MarketTradingDay(date, true));
    Member member =
        members.saveAndFlush(
            Member.createGoogleMember(
                "us-stock-test", "test@example.invalid", "us-stock-test", null));
    client(member, MemberRole.USER).post("/api/admin/stocks/us/sync").then().statusCode(403);
    assertThat(EODHD.getRequestCount()).isZero();
    json(
        """
        [{"Code":"AAPL","Name":"Apple","Exchange":"NASDAQ","Currency":"USD","Type":"Common Stock"},
         {"Code":"VTI","Name":"Vanguard Total Stock Market ETF","Exchange":"NYSE ARCA","Currency":"USD","Type":"ETF"}]
        """);
    client(member, MemberRole.ADMIN)
        .post("/api/admin/stocks/us/sync")
        .then()
        .statusCode(200)
        .body("createdCount", equalTo(2));
    assertThat(assets.findById(korean.getId()).orElseThrow().isListed()).isTrue();
    json(pricesJson());
    client(member, MemberRole.ADMIN)
        .queryParam("date", date.toString())
        .post("/api/admin/stocks/us/prices/sync")
        .then()
        .statusCode(200)
        .body("createdCount", equalTo(2));
    json(pricesJson());
    client(member, MemberRole.ADMIN)
        .queryParam("date", date.toString())
        .post("/api/admin/stocks/us/prices/sync")
        .then()
        .statusCode(200)
        .body("createdCount", equalTo(0))
        .body("updatedCount", equalTo(0));
    assertThat(calendar.findById(date).orElseThrow().getCollectedAt()).isNull();
    var apple = assets.findByCategoryAndAssetCode(AssetCategory.STOCK, "AAPL.US").orElseThrow();
    assertThat(
            prices
                .findFirstByAssetIdOrderByBaseDateDesc(apple.getId())
                .orElseThrow()
                .getClosePrice())
        .isEqualByComparingTo("119.1234");
    given()
        .port(port)
        .queryParam("q", "Apple")
        .get("/api/stocks")
        .then()
        .statusCode(200)
        .body("stocks.size()", equalTo(1))
        .body("stocks[0].assetCode", equalTo("123456"));
    given()
        .port(port)
        .queryParam("q", "Apple")
        .queryParam("country", "US")
        .get("/api/stocks")
        .then()
        .statusCode(200)
        .body("stocks.size()", equalTo(1))
        .body("stocks[0].currency", equalTo("USD"));
    given()
        .port(port)
        .get("/api/stocks/" + apple.getId() + "/price")
        .then()
        .statusCode(200)
        .body("currency", equalTo("USD"))
        .body("baseDate", equalTo(date.toString()));
  }

  private String pricesJson() {
    return """
        [{"code":"AAPL","exchange":"US","date":"2020-10-16","close":119.1234,"adjusted_close":110.0},
         {"code":"VTI","exchange":"US","date":"2020-10-16","close":177.25}]
        """;
  }

  private void json(String body) {
    EODHD.enqueue(new MockResponse().setHeader("Content-Type", "application/json").setBody(body));
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
