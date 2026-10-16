package org.grit.daynomy.loadtest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.MemberRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PortfolioLoadtestToolTest {
  private static final String NAMESPACE = "lt496-20261015";
  @TempDir Path temporary;

  @Test
  void issuesRealUserTokensWithSeparateWarmupAndSelectsLargestHolding() throws Exception {
    var issuer =
        new JwtTokenProvider(
            "daynomy", Base64.getEncoder().encodeToString(new byte[32]), 1800000, 3600000);
    JsonNode data = fixture();
    ObjectNode files =
        PortfolioLoadtestTool.tokenData(
            data, NAMESPACE, "https://dev.example.invalid", "a".repeat(64), issuer);
    JsonNode user = files.path("users").get(0);
    JsonNode warmup = files.path("warmup").get(0);
    assertThat(files.path("users").size()).isEqualTo(50);
    assertThat(files.path("warmup").size()).isEqualTo(3);
    var parsed = issuer.parseAccessToken(user.path("accessToken").asText());
    assertThat(issuer.getMemberId(parsed)).isEqualTo(11L);
    assertThat(issuer.getRole(parsed)).isEqualTo(MemberRole.USER);
    assertThat(user.path("assetId").asLong()).isEqualTo(2L);
    assertThat(user.path("initialQuantity").asLong()).isEqualTo(200);
    assertThat(user.path("fixtureHash").asText()).isEqualTo("a".repeat(64));
    assertThat(user.path("baseUrl").asText()).isEqualTo("https://dev.example.invalid");
    assertThat(warmup.path("memberId").asLong()).isEqualTo(61L);
    assertThat(warmup.path("profile").asText()).isEqualTo("light");
    assertThat(warmup.path("accessToken").asText()).isNotEqualTo(user.path("accessToken").asText());
    assertThat(issuer.getMemberId(issuer.parseAccessToken(warmup.path("accessToken").asText())))
        .isEqualTo(61L);
  }

  @Test
  void rejectsRealMembersForeignRowsAndBroadNamespaces() throws Exception {
    ObjectNode data = (ObjectNode) fixture();
    PortfolioLoadtestTool.validateData(data, NAMESPACE);
    ((ObjectNode) data.path("members").get(0)).put("role", "ADMIN");
    assertThatThrownBy(() -> PortfolioLoadtestTool.validateData(data, NAMESPACE))
        .hasMessageContaining("non-test");
    ObjectNode foreign = (ObjectNode) fixture();
    ((ObjectNode) foreign.path("holdings").get(0)).put("portfolio_id", 999);
    JsonNode poisoned = foreign;
    assertThatThrownBy(() -> PortfolioLoadtestTool.validateData(poisoned, NAMESPACE))
        .hasMessageContaining("Foreign row");
    assertThatThrownBy(() -> PortfolioLoadtestTool.validateNamespace("lt496-%"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void privateFilesPreserveDecimalPrecisionAndDisallowEscapingLocalDirectory() throws Exception {
    JsonNode money = PortfolioLoadtestTool.JSON.readTree("{\"amount\":123456789012345.67}");
    Path file = temporary.resolve("private.json");
    PortfolioLoadtestTool.writePrivate(file, money);
    JsonNode restored = PortfolioLoadtestTool.JSON.readTree(Files.readString(file));
    assertThat(restored.path("amount").decimalValue()).isEqualByComparingTo("123456789012345.67");
    assertThat(PortfolioLoadtestTool.fingerprint(restored))
        .isEqualTo(PortfolioLoadtestTool.fingerprint(money));
    assertThat(Files.getPosixFilePermissions(file))
        .isEqualTo(PosixFilePermissions.fromString("rw-------"));
    assertThatThrownBy(() -> PortfolioLoadtestTool.localFile("../../public.json"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void comparesNumericValuesAcrossJsonRoundTripsWithoutIgnoringRealChanges() throws Exception {
    JsonNode live =
        PortfolioLoadtestTool.JSON.readTree(
            "{\"amounts\":[0.00,100.00,123456789012345.67],\"status\":\"ACTIVE\"}");
    JsonNode serialized = PortfolioLoadtestTool.JSON.readTree(live.toString());
    assertThat(PortfolioLoadtestTool.sameData(live, serialized)).isTrue();
    assertThat(PortfolioLoadtestTool.sameData(serialized, live)).isTrue();

    JsonNode changedAmount =
        PortfolioLoadtestTool.JSON.readTree(
            "{\"amounts\":[0,100,123456789012345.68],\"status\":\"ACTIVE\"}");
    assertThat(PortfolioLoadtestTool.sameData(live, changedAmount)).isFalse();
    JsonNode changedType =
        PortfolioLoadtestTool.JSON.readTree(
            "{\"amounts\":[\"0\",100,123456789012345.67],\"status\":\"ACTIVE\"}");
    assertThat(PortfolioLoadtestTool.sameData(live, changedType)).isFalse();
    ObjectNode changedIdentity = (ObjectNode) serialized.deepCopy();
    changedIdentity.put("status", "INACTIVE");
    assertThat(PortfolioLoadtestTool.sameData(live, changedIdentity)).isFalse();
  }

  @Test
  void environmentFileIsParsedAsDataWithoutExecutingShell() throws Exception {
    Path file = temporary.resolve("dev.env");
    Files.writeString(file, "# comment\nLOADTEST_VALUE='$(do-not-execute)'\n");
    assertThat(PortfolioLoadtestTool.readEnvironment(file.toString()).get("LOADTEST_VALUE"))
        .isEqualTo("$(do-not-execute)");
  }

  static JsonNode fixture() throws Exception {
    ObjectNode data = PortfolioLoadtestTool.JSON.createObjectNode();
    var members = data.putArray("members");
    var portfolios = data.putArray("portfolios");
    var holdings = data.putArray("holdings");
    var histories = data.putArray("histories");
    var snapshots = data.putArray("snapshots");
    for (int number = 1; number <= 53; number++) {
      members
          .addObject()
          .put("id", number + 10)
          .put("provider", "GOOGLE")
          .put("provider_id", NAMESPACE + "-" + number)
          .put("email", NAMESPACE + "-" + number + "@example.invalid")
          .put("role", "USER")
          .put("status", "ACTIVE");
      portfolios.addObject().put("id", number + 20).put("member_id", number + 10);
      holdings
          .addObject()
          .put("id", number * 2)
          .put("portfolio_id", number + 20)
          .put("asset_id", 1)
          .put("quantity", 100);
      holdings
          .addObject()
          .put("id", number * 2 + 1)
          .put("portfolio_id", number + 20)
          .put("asset_id", 2)
          .put("quantity", 200);
      histories.addObject().put("portfolio_id", number + 20).put("effective_date", "2025-10-01");
      snapshots.addObject().put("portfolio_id", number + 20).put("base_date", "2026-10-14");
    }
    data.set(
        "prices",
        PortfolioLoadtestTool.JSON.readTree(
            """
      [{"asset_id":1,"base_date":"2026-10-14","close_price":10000.25},
       {"asset_id":2,"base_date":"2026-10-14","close_price":20000.25}]
      """));
    data.set("priceDates", PortfolioLoadtestTool.JSON.readTree("[\"2026-10-14\"]"));
    return data;
  }
}
