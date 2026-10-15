package org.grit.daynomy.loadtest;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.MemberRole;

/** One-off dev tooling, excluded from the application jar. Never starts a web server. */
public final class PortfolioLoadtestTool {
  static final ObjectMapper JSON =
      JsonMapper.builder().enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS).build();
  static final Path LOCAL = Path.of("loadtest/.local").toAbsolutePath().normalize();
  private static final List<String> TABLES = List.of("holdings", "histories", "snapshots");
  private static final Map<String, String> SQL_TABLES =
      Map.of(
          "holdings", "portfolio_holdings",
          "histories", "portfolio_holding_histories",
          "snapshots", "portfolio_daily_snapshots");

  private PortfolioLoadtestTool() {}

  public static void main(String[] args) throws Exception {
    try {
      run(args);
    } catch (Exception failure) {
      // JDBC exceptions can contain connection information; do not print credentials or tokens.
      System.err.println(
          "Preparation failed ("
              + failure.getClass().getSimpleName()
              + "). Check private dev settings, cohort and baseline. No credentials or tokens printed.");
      System.exit(1);
    }
  }

  private static void run(String[] args) throws Exception {
    require(
        args.length >= 1,
        "Use capture, restore, check or tokens with --env-file, --namespace and --base-url");
    String command = args[0];
    require(List.of("capture", "restore", "check", "tokens").contains(command), "Unknown command");
    Map<String, String> options = new HashMap<>();
    for (int i = 1; i < args.length; i += 2) {
      require(i + 1 < args.length && args[i].startsWith("--"), "Expected --option value");
      require(
          List.of("--env-file", "--namespace", "--base-url", "--snapshot", "--token-seconds")
              .contains(args[i]),
          "Unknown option");
      require(options.put(args[i], args[i + 1]) == null, "Duplicate option");
    }
    String namespace = options.getOrDefault("--namespace", "");
    validateNamespace(namespace);
    String origin = options.getOrDefault("--base-url", "");
    URI api = URI.create(origin);
    require(
        List.of("http", "https").contains(api.getScheme())
            && api.getHost() != null
            && api.getUserInfo() == null
            && (api.getPath().isEmpty() || api.getPath().equals("/"))
            && api.getQuery() == null
            && api.getFragment() == null,
        "--base-url must be the dev API origin");
    origin = origin.replaceAll("/$", "");
    Map<String, String> env = readEnvironment(options.get("--env-file"));
    require(
        "dev".equals(setting(env, "LOADTEST_ENVIRONMENT")),
        "Set LOADTEST_ENVIRONMENT=dev in the private preparation environment");
    String url = setting(env, "DB_URL");
    String username = setting(env, "DB_USERNAME");
    require(url.startsWith("jdbc:postgresql://"), "DB_URL must be a PostgreSQL JDBC URL");
    URI jdbc = URI.create(url.substring(5));
    require(
        jdbc.getUserInfo() == null
            && (jdbc.getQuery() == null
                || !jdbc.getQuery().matches("(?i).*(password|user|token|secret)=.*")),
        "Keep credentials in DB_USERNAME/DB_PASSWORD, not DB_URL");
    Path snapshotFile = localFile(options.getOrDefault("--snapshot", "baseline.json"));
    ObjectNode target =
        JSON.createObjectNode()
            .put("jdbcUrl", url)
            .put("username", username)
            .put("baseUrl", origin);
    Properties properties = new Properties();
    properties.setProperty("user", username);
    properties.setProperty("password", setting(env, "DB_PASSWORD"));
    properties.setProperty("connectTimeout", "5");
    properties.setProperty("ApplicationName", "portfolio-loadtest-preparation");
    try (Connection connection = DriverManager.getConnection(url, properties)) {
      connection.setAutoCommit(false);
      try (var statement = connection.createStatement()) {
        statement.execute("SET LOCAL statement_timeout = '60s'");
        statement.execute("SET LOCAL lock_timeout = '5s'");
      }
      if (command.equals("capture")) {
        require(
            !Files.exists(snapshotFile),
            "Baseline already exists; use another --snapshot filename");
        JsonNode data = readData(connection, namespace);
        validateData(data, namespace);
        validateCohort(data, namespace);
        ObjectNode snapshot = JSON.createObjectNode();
        snapshot
            .put("schemaVersion", 1)
            .put("namespace", namespace)
            .put("capturedAt", Instant.now().toString());
        snapshot.set("target", target);
        snapshot.set("data", data);
        snapshot.put("fingerprint", fingerprint(data));
        writePrivate(snapshotFile, snapshot);
        connection.rollback();
        System.out.println(
            "Captured " + data.path("members").size() + " test members; baseline saved privately.");
        return;
      }
      JsonNode snapshot = JSON.readTree(Files.readString(snapshotFile));
      require(snapshot.path("schemaVersion").asInt() == 1, "Unsupported baseline version");
      require(snapshot.path("namespace").asText().equals(namespace), "Baseline namespace mismatch");
      require(snapshot.path("target").equals(target), "Baseline DB/API target mismatch");
      JsonNode data = snapshot.path("data");
      validateData(data, namespace);
      validateCohort(data, namespace);
      require(
          fingerprint(data).equals(snapshot.path("fingerprint").asText()),
          "Baseline fingerprint mismatch");
      if (command.equals("check")) {
        lockTestMembers(connection, namespace);
        connection.rollback();
        System.out.println(
            "Test-member locks are idle. This does not prove all background DB work is idle.");
      } else if (command.equals("restore")) {
        restore(connection, namespace, data);
        connection.commit();
        System.out.println(
            "Restored only the captured test holdings, histories and snapshots. Sequences unchanged.");
      } else {
        require(
            readData(connection, namespace).equals(data),
            "DB differs from baseline; restore before issuing tokens");
        JwtTokenProvider issuer =
            new JwtTokenProvider(
                env.getOrDefault("JWT_ISSUER", "daynomy"),
                setting(env, "JWT_SECRET"),
                tokenExpiration(options, env),
                Long.parseLong(env.getOrDefault("REFRESH_TOKEN_EXPIRATION", "1209600000")));
        ObjectNode tokenFiles =
            tokenData(data, namespace, origin, snapshot.path("fingerprint").asText(), issuer);
        writePrivate(localFile("users.json"), tokenFiles.path("users"));
        writePrivate(localFile("warmup-users.json"), tokenFiles.path("warmup"));
        connection.rollback();
        System.out.println(
            "Issued USER access tokens into loadtest/.local. No refresh tokens stored.");
      }
    }
  }

  private static long tokenExpiration(Map<String, String> options, Map<String, String> env) {
    long seconds =
        Long.parseLong(
            options.getOrDefault(
                "--token-seconds",
                Long.toString(
                    Long.parseLong(env.getOrDefault("ACCESS_TOKEN_EXPIRATION", "1800000"))
                        / 1000)));
    require(seconds >= 60 && seconds <= 7200, "Test token lifetime must be 60..7200 seconds");
    return seconds * 1000;
  }

  static void validateNamespace(String namespace) {
    require(namespace.matches("lt496-[0-9]{8}"), "Namespace must be lt496-YYYYMMDD");
  }

  static Map<String, String> readEnvironment(String filename) throws Exception {
    Map<String, String> env = new HashMap<>(System.getenv());
    if (filename != null) {
      for (String line : Files.readAllLines(Path.of(filename))) {
        String text = line.strip();
        if (text.isEmpty() || text.startsWith("#")) continue;
        int equals = text.indexOf('=');
        require(equals > 0, "Invalid environment file; use KEY=value lines");
        String value = text.substring(equals + 1).strip();
        if ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))
          value = value.substring(1, value.length() - 1);
        env.put(text.substring(0, equals).strip(), value);
      }
    }
    return env;
  }

  private static String setting(Map<String, String> env, String key) {
    require(env.get(key) != null && !env.get(key).isBlank(), "Missing environment setting: " + key);
    return env.get(key);
  }

  static Path localFile(String filename) {
    Path file = LOCAL.resolve(filename).normalize();
    require(
        file.startsWith(LOCAL) && !file.equals(LOCAL), "Output files must stay in loadtest/.local");
    return file;
  }

  static void writePrivate(Path file, JsonNode data) throws Exception {
    Files.createDirectories(file.getParent());
    Path temporary = Files.createTempFile(file.getParent(), ".prepare-", ".json");
    try {
      Files.setPosixFilePermissions(temporary, PosixFilePermissions.fromString("rw-------"));
      Files.writeString(temporary, JSON.writerWithDefaultPrettyPrinter().writeValueAsString(data));
      Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temporary);
    }
  }

  static String fingerprint(JsonNode data) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256")
                .digest(data.toString().getBytes(StandardCharsets.UTF_8)));
  }

  static JsonNode readData(Connection connection, String namespace) throws Exception {
    validateNamespace(namespace);
    String sql =
        """
        WITH m AS (SELECT * FROM members WHERE provider_id LIKE ? ORDER BY id),
             p AS (SELECT p.* FROM portfolios p JOIN m ON m.id = p.member_id ORDER BY p.id),
             a AS (SELECT DISTINCT h.asset_id FROM portfolio_holding_histories h JOIN p ON p.id = h.portfolio_id)
        SELECT jsonb_build_object(
          'members', COALESCE((SELECT jsonb_agg(jsonb_build_object('id',id,'provider',provider,
            'provider_id',provider_id,'email',email,'role',role,'status',status) ORDER BY id) FROM m),'[]'::jsonb),
          'portfolios', COALESCE((SELECT jsonb_agg(jsonb_build_object('id',id,'member_id',member_id) ORDER BY id) FROM p),'[]'::jsonb),
          'holdings', COALESCE((SELECT jsonb_agg(to_jsonb(h) ORDER BY h.id) FROM portfolio_holdings h JOIN p ON p.id=h.portfolio_id),'[]'::jsonb),
          'histories', COALESCE((SELECT jsonb_agg(to_jsonb(h) ORDER BY h.id) FROM portfolio_holding_histories h JOIN p ON p.id=h.portfolio_id),'[]'::jsonb),
          'snapshots', COALESCE((SELECT jsonb_agg(to_jsonb(s) ORDER BY s.id) FROM portfolio_daily_snapshots s JOIN p ON p.id=s.portfolio_id),'[]'::jsonb),
          'prices', COALESCE((SELECT jsonb_agg(jsonb_build_object('asset_id',s.asset_id,'base_date',s.base_date,'close_price',s.close_price)
            ORDER BY s.asset_id,s.base_date) FROM stock_daily_prices s JOIN a ON a.asset_id=s.asset_id),'[]'::jsonb),
          'priceDates', COALESCE((SELECT jsonb_agg(d.base_date ORDER BY d.base_date) FROM (SELECT DISTINCT base_date FROM stock_daily_prices) d),'[]'::jsonb))
        """;
    try (var statement = connection.prepareStatement(sql)) {
      statement.setString(1, namespace + "-%");
      try (var result = statement.executeQuery()) {
        result.next();
        return JSON.readTree(result.getString(1));
      }
    }
  }

  static void validateData(JsonNode data, String namespace) {
    validateNamespace(namespace);
    List<Long> members = new ArrayList<>();
    for (JsonNode member : data.path("members")) {
      require(
          member.path("id").asLong() > 0
              && member.path("provider").asText().equals("GOOGLE")
              && member.path("provider_id").asText().matches(namespace + "-[0-9]+")
              && member
                  .path("email")
                  .asText()
                  .equals(member.path("provider_id").asText() + "@example.invalid")
              && member.path("role").asText().equals("USER")
              && member.path("status").asText().equals("ACTIVE"),
          "Baseline contains a non-test member");
      members.add(member.path("id").asLong());
    }
    require(
        !members.isEmpty() && members.size() <= 1000,
        "No test members found, or dataset exceeds preparation limit");
    List<Long> portfolios = new ArrayList<>();
    for (JsonNode portfolio : data.path("portfolios")) {
      require(
          members.contains(portfolio.path("member_id").asLong()), "Foreign portfolio in baseline");
      portfolios.add(portfolio.path("id").asLong());
    }
    require(portfolios.size() == members.size(), "Each test member needs one portfolio");
    for (String table : TABLES) {
      require(data.path(table).isArray(), "Missing baseline table");
      for (JsonNode row : data.path(table)) {
        require(portfolios.contains(row.path("portfolio_id").asLong()), "Foreign row in baseline");
      }
    }
  }

  static void lockTestMembers(Connection connection, String namespace) throws Exception {
    validateNamespace(namespace);
    try (var statement =
        connection.prepareStatement(
            "SELECT id FROM members WHERE provider_id LIKE ? ORDER BY id FOR UPDATE NOWAIT")) {
      statement.setString(1, namespace + "-%");
      try (var result = statement.executeQuery()) {
        while (result.next()) {
          /* Drain locked rows. */
        }
      }
    }
    try (var statement =
        connection.prepareStatement(
            "SELECT p.id FROM portfolios p JOIN members m ON m.id=p.member_id WHERE m.provider_id LIKE ? ORDER BY p.id FOR UPDATE OF p NOWAIT")) {
      statement.setString(1, namespace + "-%");
      try (var result = statement.executeQuery()) {
        while (result.next()) {
          /* Drain locked rows. */
        }
      }
    }
  }

  static void restore(Connection connection, String namespace, JsonNode baseline) throws Exception {
    validateData(baseline, namespace);
    lockTestMembers(connection, namespace);
    var ids =
        connection.createArrayOf(
            "bigint",
            JSON.convertValue(baseline.path("portfolios").findValues("id"), Long[].class));
    try (var statement =
        connection.prepareStatement(
            "SELECT id FROM portfolios WHERE id = ANY (?) ORDER BY id FOR UPDATE")) {
      statement.setArray(1, ids);
      try (var result = statement.executeQuery()) {
        while (result.next()) {
          /* Drain locked rows. */
        }
      }
    }
    JsonNode current = readData(connection, namespace);
    require(
        current.path("members").equals(baseline.path("members"))
            && current.path("portfolios").equals(baseline.path("portfolios")),
        "Test member or portfolio identity changed");
    require(
        current.path("prices").equals(baseline.path("prices"))
            && current.path("priceDates").equals(baseline.path("priceDates")),
        "Price data changed; do not compare against this baseline");
    for (String table : List.of("snapshots", "histories", "holdings")) {
      try (var statement =
          connection.prepareStatement(
              "DELETE FROM " + SQL_TABLES.get(table) + " WHERE portfolio_id = ANY (?)")) {
        statement.setArray(1, ids);
        statement.executeUpdate();
      }
    }
    for (String table : TABLES) {
      String name = SQL_TABLES.get(table);
      try (var statement =
          connection.prepareStatement(
              "INSERT INTO "
                  + name
                  + " SELECT * FROM jsonb_populate_recordset(NULL::"
                  + name
                  + ", ?::jsonb)")) {
        statement.setString(1, baseline.path(table).toString());
        statement.executeUpdate();
      }
    }
    require(
        readData(connection, namespace).equals(baseline),
        "Restored data differs; transaction will be rolled back");
  }

  static ObjectNode tokenData(
      JsonNode data, String namespace, String origin, String hash, JwtTokenProvider issuer)
      throws Exception {
    ArrayNode users = JSON.createArrayNode();
    validateCohort(data, namespace);
    ArrayNode warmup = JSON.createArrayNode();
    List<JsonNode> ordered = new ArrayList<>();
    data.path("members").forEach(ordered::add);
    ordered.sort(Comparator.comparingInt(member -> memberNumber(member, namespace)));
    for (JsonNode member : ordered) {
      int number = memberNumber(member, namespace);
      long memberId = member.path("id").asLong();
      long portfolioId = 0;
      for (JsonNode portfolio : data.path("portfolios"))
        if (portfolio.path("member_id").asLong() == memberId)
          portfolioId = portfolio.path("id").asLong();
      JsonNode holding = null;
      for (JsonNode row : data.path("holdings")) {
        if (row.path("portfolio_id").asLong() == portfolioId
            && (holding == null
                || row.path("quantity").asLong() > holding.path("quantity").asLong()))
          holding = row;
      }
      require(
          holding != null && holding.path("quantity").asLong() > 0, "Test member has no holding");
      JsonNode price = null;
      for (JsonNode row : data.path("prices"))
        if (row.path("asset_id").asLong() == holding.path("asset_id").asLong()
            && (price == null
                || row.path("base_date").asText().compareTo(price.path("base_date").asText()) > 0))
          price = row;
      require(price != null, "Test holding has no price");
      var pair = issuer.createTokenPair(memberId, MemberRole.USER);
      ObjectNode user =
          JSON.createObjectNode()
              .put("testNumber", number)
              .put(
                  "referenceDate",
                  LocalDate.parse(
                          namespace.substring(6), java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                      .toString())
              .put("memberId", memberId)
              .put("assetId", holding.path("asset_id").asLong())
              .put("initialQuantity", holding.path("quantity").asLong())
              .put("accessToken", pair.accessToken())
              .put("baseUrl", origin)
              .put("fixtureHash", hash)
              .put("profile", profile(number));
      String earliest = "9999-12-31";
      String latest = "0000-01-01";
      for (JsonNode row : data.path("histories"))
        if (row.path("portfolio_id").asLong() == portfolioId
            && row.path("effective_date").asText().compareTo(earliest) < 0)
          earliest = row.path("effective_date").asText();
      for (JsonNode row : data.path("snapshots"))
        if (row.path("portfolio_id").asLong() == portfolioId
            && row.path("base_date").asText().compareTo(latest) > 0)
          latest = row.path("base_date").asText();
      user.put("earliestHistoryDate", earliest).put("latestSnapshotDate", latest);
      user.set("unitPrice", price.path("close_price"));
      if (number > 50) warmup.add(user);
      else users.add(user);
    }
    require(
        warmup.size() == 3 && users.size() == 50, "Expected 50 measurement and 3 warmup members");
    ObjectNode files = JSON.createObjectNode();
    files.set("users", users);
    files.set("warmup", warmup);
    return files;
  }

  static int memberNumber(JsonNode member, String namespace) {
    return Integer.parseInt(member.path("provider_id").asText().substring(namespace.length() + 1));
  }

  static void validateCohort(JsonNode data, String namespace) {
    validateData(data, namespace);
    var numbers = new java.util.HashSet<Integer>();
    for (JsonNode member : data.path("members")) numbers.add(memberNumber(member, namespace));
    require(
        numbers.size() == 53
            && numbers.containsAll(java.util.stream.IntStream.rangeClosed(1, 53).boxed().toList()),
        "Expected exactly the 1..53 test-member cohort (50 measurement + 3 warmup)");
  }

  static String profile(int number) {
    if (number > 50) return List.of("light", "standard", "heavy").get(number - 51);
    return switch (number % 5) {
      case 1, 3 -> "light";
      case 2, 4 -> "standard";
      default -> "heavy";
    };
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }
}
