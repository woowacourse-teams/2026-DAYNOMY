package org.grit.daynomy.loadtest;

import static org.grit.daynomy.loadtest.PortfolioLoadtestTool.JSON;
import static org.grit.daynomy.loadtest.PortfolioLoadtestTool.require;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import org.grit.daynomy.auth.token.JwtTokenProvider;
import org.grit.daynomy.member.domain.MemberRole;

/** Manual dev preparation only. Does not start Spring, an HTTP server, or schedulers. */
public final class Portfolio1000LoadtestTool {
  private static final List<String> TABLES =
      List.of("portfolio_holdings", "portfolio_holding_histories", "portfolio_daily_snapshots");
  private static final String PORTFOLIOS =
      "SELECT p.id FROM public.portfolios p JOIN public.members m ON m.id=p.member_id WHERE m.provider='GOOGLE' AND m.provider_id LIKE ?";

  private Portfolio1000LoadtestTool() {}

  public static void main(String[] args) {
    try {
      run(args);
    } catch (NumberFormatException failure) {
      System.err.println("Preparation refused: invalid numeric setting.");
      System.exit(1);
    } catch (IllegalArgumentException failure) {
      System.err.println("Preparation refused: " + failure.getMessage());
      System.exit(1);
    } catch (Exception failure) {
      System.err.println(
          "Preparation failed ("
              + failure.getClass().getSimpleName()
              + "). Check dev connectivity and the private files. Credentials and request details omitted.");
      System.exit(1);
    }
  }

  private static void run(String[] args) throws Exception {
    require(
        args.length >= 1 && List.of("capture", "tokens", "restore").contains(args[0]),
        "Use capture, tokens, or restore.");
    Map<String, String> options = new HashMap<>();
    for (int i = 1; i < args.length; i += 2) {
      require(
          i + 1 < args.length
              && List.of("--env-file", "--namespace", "--base-url", "--token-seconds")
                  .contains(args[i]),
          "Invalid option.");
      require(options.put(args[i], args[i + 1]) == null, "Duplicate option.");
    }
    String namespace = options.getOrDefault("--namespace", "lt1000-20261029");
    require(namespace.matches("lt1000-[0-9]{8}"), "Use namespace lt1000-YYYYMMDD.");
    LocalDate.parse(namespace.substring(7), DateTimeFormatter.BASIC_ISO_DATE);
    String origin = options.getOrDefault("--base-url", "https://dev.daynomy.com");
    require(
        origin.matches("https://[a-z0-9.-]*\\bdev\\b[a-z0-9.-]*(?::[0-9]+)?"),
        "Use the HTTPS dev API origin.");
    Map<String, String> env = PortfolioLoadtestTool.readEnvironment(options.get("--env-file"));
    require("dev".equals(env.get("LOADTEST_ENVIRONMENT")), "Set LOADTEST_ENVIRONMENT=dev.");
    String url = setting(env, "DB_URL");
    require(
        url.startsWith("jdbc:postgresql://")
            && !url.matches("(?i).*(password=|user=|token=|secret=|@).*"),
        "Keep DB credentials outside DB_URL.");
    Properties properties = new Properties();
    properties.setProperty("user", setting(env, "DB_USERNAME"));
    properties.setProperty("password", setting(env, "DB_PASSWORD"));
    properties.setProperty("connectTimeout", "5");
    properties.setProperty("ApplicationName", "portfolio-1000-preparation");
    Path directory = PortfolioLoadtestTool.localFile(namespace + "-baseline");
    try (Connection connection = DriverManager.getConnection(url, properties)) {
      connection.setAutoCommit(false);
      try (var s = connection.createStatement()) {
        s.execute(
            "SET LOCAL TIME ZONE 'Asia/Seoul'; SET LOCAL search_path=public,pg_catalog; SET LOCAL lock_timeout='5s'; SET LOCAL statement_timeout='10min'");
        try (var r = s.executeQuery("SELECT current_database()")) {
          r.next();
          require("daynomy_dev".equals(r.getString(1)), "The DB must be daynomy_dev.");
        }
      }
      validateCohort(connection, namespace);
      if (args[0].equals("capture")) {
        capture(connection, namespace, origin, url, directory);
        connection.rollback();
        System.out.println(
            "Captured 1,003 test members and streamed their baseline into private files.");
      } else {
        ObjectNode manifest =
            (ObjectNode) JSON.readTree(Files.readString(directory.resolve("manifest.json")));
        validateManifest(connection, namespace, origin, url, directory, manifest);
        if (args[0].equals("restore")) {
          restore(connection, namespace, directory, manifest);
          connection.commit();
          System.out.println(
              "Restored only the 1,003 captured test portfolios. Identity sequences unchanged.");
        } else {
          verifyRows(connection, namespace, manifest);
          long seconds = Long.parseLong(options.getOrDefault("--token-seconds", "86400"));
          require(seconds >= 900 && seconds <= 86400, "Token lifetime must be 900..86400 seconds.");
          JwtTokenProvider issuer =
              new JwtTokenProvider(
                  env.getOrDefault("JWT_ISSUER", "daynomy"),
                  setting(env, "JWT_SECRET"),
                  seconds * 1000,
                  7200000);
          ArrayNode accounts =
              accounts(
                  connection,
                  namespace,
                  origin,
                  fileHash(directory.resolve("manifest.json")),
                  issuer);
          PortfolioLoadtestTool.writePrivate(
              PortfolioLoadtestTool.localFile("accounts-1000.json"), accounts);
          connection.rollback();
          System.out.println(
              "Issued 1,003 USER access tokens into loadtest/.local/accounts-1000.json (0600). No token values printed; no refresh tokens persisted.");
        }
      }
    }
  }

  private static String setting(Map<String, String> env, String name) {
    require(env.get(name) != null && !env.get(name).isBlank(), "Missing setting: " + name);
    return env.get(name);
  }

  static int profileHoldings(int number) {
    return switch (profile(number)) {
      case "light" -> 5;
      case "standard" -> 15;
      default -> 30;
    };
  }

  static String profile(int number) {
    require(number >= 1 && number <= 1003, "Invalid test member number.");
    if (number > 1000) return List.of("light", "standard", "heavy").get(number - 1001);
    return switch (number % 5) {
      case 1, 3 -> "light";
      case 2, 4 -> "standard";
      default -> "heavy";
    };
  }

  static void validateCohort(Connection c, String namespace) throws Exception {
    String query =
        "SELECT m.provider_id,m.email,m.role,m.status,p.id,"
            + "(SELECT count(*) FROM portfolio_holdings h WHERE h.portfolio_id=p.id),"
            + "(SELECT count(*) FROM portfolio_holding_histories h WHERE h.portfolio_id=p.id AND h.change_type IN ('BUY','SELL')) "
            + "FROM members m LEFT JOIN portfolios p ON p.member_id=m.id WHERE m.provider='GOOGLE' AND m.provider_id LIKE ?";
    var seen = new java.util.HashSet<Integer>();
    try (var s = c.prepareStatement(query)) {
      s.setString(1, namespace + "-%");
      try (var r = s.executeQuery()) {
        while (r.next()) {
          String providerId = r.getString(1);
          require(providerId.matches(namespace + "-[0-9]+"), "Unexpected member in namespace.");
          int n = Integer.parseInt(providerId.substring(namespace.length() + 1));
          require(n >= 1 && n <= 1003 && seen.add(n), "Duplicate or out-of-range test member.");
          require(
              (providerId + "@example.invalid").equals(r.getString(2))
                  && "USER".equals(r.getString(3))
                  && "ACTIVE".equals(r.getString(4))
                  && r.getLong(5) > 0,
              "A member is not an active test USER with a portfolio.");
          // Restores must also work when measured writes changed the current portfolio rows.
        }
      }
    }
    require(seen.size() == 1003, "Expected exactly test members 1..1003.");
  }

  private static String rowsQuery(String table) {
    return "SELECT to_jsonb(t)::text FROM public."
        + table
        + " t WHERE t.portfolio_id IN ("
        + PORTFOLIOS
        + ") ORDER BY t.id";
  }

  private static String hashQuery(Connection c, String query, String namespace) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (var s = c.prepareStatement(query)) {
      s.setFetchSize(500);
      s.setString(1, namespace + "-%");
      try (var r = s.executeQuery()) {
        while (r.next()) digest.update((r.getString(1) + "\n").getBytes(StandardCharsets.UTF_8));
      }
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static String identity(Connection c, String ns) throws Exception {
    return hashQuery(
        c,
        "SELECT jsonb_build_array(m.id,m.provider,m.provider_id,m.email,m.role,m.status,p.id)::text FROM members m JOIN portfolios p ON p.member_id=m.id WHERE m.provider='GOOGLE' AND m.provider_id LIKE ? ORDER BY m.id",
        ns);
  }

  private static String references(Connection c, String ns) throws Exception {
    return hashQuery(
        c,
        "WITH ids AS (SELECT DISTINCT h.asset_id FROM portfolio_holding_histories h WHERE h.portfolio_id IN ("
            + PORTFOLIOS
            + ")) "
            + "SELECT v FROM (SELECT 'asset:'||a.id AS k,jsonb_build_array(a.id,a.asset_code,a.category,a.market)::text AS v FROM assets a JOIN ids ON ids.asset_id=a.id "
            + "UNION ALL SELECT 'price:'||p.asset_id||':'||p.base_date,jsonb_build_array(p.asset_id,p.base_date,p.close_price)::text FROM stock_daily_prices p JOIN ids ON ids.asset_id=p.asset_id "
            + "UNION ALL SELECT 'rate:'||requested_date,jsonb_build_array(requested_date,base_date,rate)::text FROM usd_krw_rates) x ORDER BY k",
        ns);
  }

  private static String schema(Connection c) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (var s = c.createStatement();
        var r =
            s.executeQuery(
                "SELECT table_name,column_name,data_type,udt_name,ordinal_position,is_nullable FROM information_schema.columns WHERE table_schema='public' AND table_name IN ('portfolio_holdings','portfolio_holding_histories','portfolio_daily_snapshots') ORDER BY table_name,ordinal_position")) {
      while (r.next())
        for (int i = 1; i <= 6; i++)
          digest.update((r.getString(i) + "\n").getBytes(StandardCharsets.UTF_8));
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  private static void lock(Connection c, String ns) throws Exception {
    for (String query :
        List.of(
            "SELECT id FROM members WHERE provider='GOOGLE' AND provider_id LIKE ? ORDER BY id FOR UPDATE NOWAIT",
            "SELECT id FROM portfolios WHERE id IN ("
                + PORTFOLIOS
                + ") ORDER BY id FOR UPDATE NOWAIT")) {
      try (var s = c.prepareStatement(query)) {
        s.setString(1, ns + "-%");
        try (var r = s.executeQuery()) {
          while (r.next()) {
            /* Acquire every test-member lock. */
          }
        }
      }
    }
  }

  static ObjectNode capture(Connection c, String ns, String origin, String target, Path directory)
      throws Exception {
    require(
        !Files.exists(directory),
        "Baseline already exists. Do not overwrite it after a measurement.");
    lock(c, ns);
    // An initial baseline must not silently include earlier measured trades.
    try (var s =
        c.prepareStatement(
            "SELECT m.provider_id,count(*) FILTER(WHERE h.change_type IN ('BUY','SELL')),(SELECT count(*) FROM portfolio_holdings q WHERE q.portfolio_id=p.id) FROM members m JOIN portfolios p ON p.member_id=m.id LEFT JOIN portfolio_holding_histories h ON h.portfolio_id=p.id WHERE m.provider='GOOGLE' AND m.provider_id LIKE ? GROUP BY m.provider_id,p.id")) {
      s.setString(1, ns + "-%");
      try (var r = s.executeQuery()) {
        while (r.next()) {
          int n = Integer.parseInt(r.getString(1).substring(ns.length() + 1));
          int expected =
              switch (profile(n)) {
                case "light" -> 50;
                case "standard" -> 200;
                default -> 800;
              };
          require(
              r.getInt(2) == expected,
              "Trade counts differ from the seeded fixture. Refusing a new baseline.");
          require(
              r.getInt(3) == profileHoldings(n),
              "Holding counts differ from the seeded fixture. Refusing a new baseline.");
        }
      }
    }
    Path parent = directory.getParent();
    Files.createDirectories(parent);
    Path temporary =
        Files.createTempDirectory(
            parent,
            ".baseline-",
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
    ObjectNode manifest =
        JSON.createObjectNode()
            .put("version", 1)
            .put("namespace", ns)
            .put("origin", origin)
            .put("target", target)
            .put("identity", identity(c, ns))
            .put("references", references(c, ns))
            .put("schema", schema(c));
    ObjectNode files = manifest.putObject("files");
    try {
      for (String table : TABLES) {
        Path file = temporary.resolve(table + ".jsonl.gz");
        Files.createFile(
            file,
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long count = 0;
        try (var s = c.prepareStatement(rowsQuery(table));
            BufferedWriter writer =
                new BufferedWriter(
                    new java.io.OutputStreamWriter(
                        new GZIPOutputStream(Files.newOutputStream(file)),
                        StandardCharsets.UTF_8))) {
          s.setString(1, ns + "-%");
          s.setFetchSize(500);
          try (var r = s.executeQuery()) {
            while (r.next()) {
              String line = r.getString(1) + "\n";
              writer.write(line);
              digest.update(line.getBytes(StandardCharsets.UTF_8));
              count++;
            }
          }
        }
        require(count > 0, "A baseline table is empty.");
        files
            .putObject(table)
            .put("rows", count)
            .put("dataHash", HexFormat.of().formatHex(digest.digest()))
            .put("fileHash", fileHash(file));
      }
      PortfolioLoadtestTool.writePrivate(temporary.resolve("manifest.json"), manifest);
      Files.move(temporary, directory);
      return manifest;
    } finally {
      if (Files.exists(temporary)) {
        try (var children = Files.list(temporary)) {
          for (Path child : children.toList()) Files.deleteIfExists(child);
        }
        Files.deleteIfExists(temporary);
      }
    }
  }

  private static String fileHash(Path path) throws Exception {
    MessageDigest digest = MessageDigest.getInstance("SHA-256");
    try (InputStream stream = Files.newInputStream(path)) {
      byte[] buffer = new byte[65536];
      int size;
      while ((size = stream.read(buffer)) != -1) digest.update(buffer, 0, size);
    }
    return HexFormat.of().formatHex(digest.digest());
  }

  static void validateManifest(
      Connection c, String ns, String origin, String target, Path directory, JsonNode manifest)
      throws Exception {
    require(
        manifest.path("version").asInt() == 1
            && ns.equals(manifest.path("namespace").asText())
            && origin.equals(manifest.path("origin").asText())
            && target.equals(manifest.path("target").asText()),
        "Baseline target or version mismatch.");
    require(
        identity(c, ns).equals(manifest.path("identity").asText()),
        "Test member identities changed.");
    require(
        references(c, ns).equals(manifest.path("references").asText()),
        "Price, asset, or FX reference data changed. Do not compare this run with the old baseline.");
    require(
        schema(c).equals(manifest.path("schema").asText()),
        "Portfolio schema changed since capture.");
    for (String table : TABLES)
      require(
          fileHash(directory.resolve(table + ".jsonl.gz"))
              .equals(manifest.path("files").path(table).path("fileHash").asText()),
          "A private baseline file is corrupted.");
  }

  private static void verifyRows(Connection c, String ns, JsonNode manifest) throws Exception {
    for (String table : TABLES)
      require(
          hashQuery(c, rowsQuery(table), ns)
              .equals(manifest.path("files").path(table).path("dataHash").asText()),
          "Portfolio rows differ from the baseline. Restore before issuing tokens.");
  }

  static void restore(Connection c, String ns, Path directory, JsonNode manifest) throws Exception {
    lock(c, ns);
    require(
        identity(c, ns).equals(manifest.path("identity").asText())
            && references(c, ns).equals(manifest.path("references").asText())
            && schema(c).equals(manifest.path("schema").asText()),
        "Baseline context changed before restore acquired its locks.");
    for (String table :
        List.of("portfolio_daily_snapshots", "portfolio_holding_histories", "portfolio_holdings")) {
      try (var s =
          c.prepareStatement(
              "DELETE FROM public." + table + " WHERE portfolio_id IN (" + PORTFOLIOS + ")")) {
        s.setString(1, ns + "-%");
        s.executeUpdate();
      }
    }
    for (String table : TABLES) {
      try (BufferedReader reader =
              new BufferedReader(
                  new java.io.InputStreamReader(
                      new GZIPInputStream(
                          Files.newInputStream(directory.resolve(table + ".jsonl.gz"))),
                      StandardCharsets.UTF_8));
          var s =
              c.prepareStatement(
                  "INSERT INTO public."
                      + table
                      + " SELECT * FROM jsonb_populate_recordset(NULL::public."
                      + table
                      + ",?::jsonb)")) {
        ArrayNode batch = JSON.createArrayNode();
        String line;
        while ((line = reader.readLine()) != null) {
          batch.add(JSON.readTree(line));
          if (batch.size() == 500) {
            s.setString(1, batch.toString());
            s.executeUpdate();
            batch.removeAll();
          }
        }
        if (!batch.isEmpty()) {
          s.setString(1, batch.toString());
          s.executeUpdate();
        }
      }
    }
    verifyRows(c, ns, manifest);
    require(
        references(c, ns).equals(manifest.path("references").asText()),
        "Reference data changed during restore; transaction must be rolled back.");
  }

  static ArrayNode accounts(
      Connection c, String ns, String origin, String hash, JwtTokenProvider issuer)
      throws Exception {
    LocalDate reference = LocalDate.parse(ns.substring(7), DateTimeFormatter.BASIC_ISO_DATE);
    LocalDate evaluation = LocalDate.now(ZoneId.of("Asia/Seoul"));
    String query =
        "SELECT m.id,m.provider_id,p.id,"
            + "(SELECT jsonb_agg(jsonb_build_object('assetId',h.asset_id,'quantity',h.quantity,'averagePurchasePrice',h.average_purchase_price) ORDER BY h.asset_id) FROM portfolio_holdings h WHERE h.portfolio_id=p.id),"
            + "chosen.asset_id,chosen.quantity,latest.close_price,latest.base_date,a.market,r.rate,"
            + "(SELECT round(coalesce(sum((h.unit_price*h.exchange_rate-h.average_purchase_price)*h.transaction_quantity-(h.fee+h.tax)*h.exchange_rate),0),2) FROM portfolio_holding_histories h WHERE h.portfolio_id=p.id AND h.change_type='SELL'),"
            + "(SELECT CASE WHEN bool_and(a.market IN ('KOSPI','KOSDAQ')) THEN 'KR' WHEN bool_and(a.market NOT IN ('KOSPI','KOSDAQ')) THEN 'US' ELSE 'MIXED' END FROM portfolio_holdings h JOIN assets a ON a.id=h.asset_id WHERE h.portfolio_id=p.id) "
            + "FROM members m JOIN portfolios p ON p.member_id=m.id "
            + "JOIN LATERAL (SELECT h.* FROM portfolio_holdings h WHERE h.portfolio_id=p.id ORDER BY h.quantity DESC,h.asset_id LIMIT 1) chosen ON true "
            + "JOIN assets a ON a.id=chosen.asset_id JOIN LATERAL (SELECT q.* FROM stock_daily_prices q WHERE q.asset_id=a.id ORDER BY q.base_date DESC LIMIT 1) latest ON true "
            + "LEFT JOIN usd_krw_rates r ON r.requested_date=latest.base_date "
            + "WHERE m.provider='GOOGLE' AND m.provider_id LIKE ? ORDER BY substring(m.provider_id FROM '[0-9]+$')::integer";
    ArrayNode result = JSON.createArrayNode();
    try (var s = c.prepareStatement(query)) {
      s.setString(1, ns + "-%");
      try (var r = s.executeQuery()) {
        while (r.next()) {
          int n = Integer.parseInt(r.getString(2).substring(ns.length() + 1));
          boolean usd = !List.of("KOSPI", "KOSDAQ").contains(r.getString(9));
          require(
              !usd || r.getBigDecimal(10) != null,
              "Latest selected US price has no stored FX rate. Fill it before token issuance.");
          ObjectNode account =
              result
                  .addObject()
                  .put("testNumber", n)
                  .put("memberId", r.getLong(1))
                  .put("portfolioId", r.getLong(3))
                  .put("namespace", ns)
                  .put("profile", profile(n))
                  .put("baseUrl", origin)
                  .put("referenceDate", reference.toString())
                  .put("evaluationDate", evaluation.toString())
                  .put("baselineHash", hash)
                  .put("assetId", r.getLong(5))
                  .put("initialQuantity", r.getLong(6))
                  .put("unitPrice", r.getBigDecimal(7))
                  .put("exchangeRate", usd ? r.getBigDecimal(10) : java.math.BigDecimal.ONE)
                  .put("currency", usd ? "USD" : "KRW")
                  .put("country", r.getString(12))
                  .put("expectedRealizedProfitLoss", r.getBigDecimal(11));
          account.set("holdings", JSON.readTree(r.getString(4)));
          ObjectNode baselines = account.putObject("transactionBaselines");
          for (int months : List.of(0, 1, 6)) {
            LocalDate day = evaluation.minusMonths(months);
            try (var count =
                c.prepareStatement(
                    "SELECT count(*) FROM portfolio_holding_histories WHERE portfolio_id=? AND effective_date=?")) {
              count.setLong(1, r.getLong(3));
              count.setObject(2, day);
              try (var rows = count.executeQuery()) {
                rows.next();
                baselines
                    .putObject(Integer.toString(months))
                    .put("date", day.toString())
                    .put("historyCount", rows.getInt(1));
              }
            }
          }
          ObjectNode performance = account.putObject("performancePeriods");
          for (int months : List.of(1, 6, 12)) {
            LocalDate from = evaluation.minusMonths(months);
            try (var count =
                c.prepareStatement(
                    "SELECT count(*),max(base_date) FROM portfolio_daily_snapshots WHERE portfolio_id=? AND base_date BETWEEN ? AND ?")) {
              count.setLong(1, r.getLong(3));
              count.setObject(2, from);
              count.setObject(3, evaluation.minusDays(1));
              try (var rows = count.executeQuery()) {
                rows.next();
                require(rows.getInt(1) >= 2, "Performance period has fewer than two snapshots.");
                performance
                    .putObject(Integer.toString(months))
                    .put("from", from.toString())
                    .put("to", evaluation.toString())
                    .put("pointCount", rows.getInt(1))
                    .put("baseDate", rows.getString(2));
              }
            }
          }
          account.put(
              "accessToken", issuer.createTokenPair(r.getLong(1), MemberRole.USER).accessToken());
        }
      }
    }
    require(result.size() == 1003, "Expected 1,003 complete account records.");
    return result;
  }
}
