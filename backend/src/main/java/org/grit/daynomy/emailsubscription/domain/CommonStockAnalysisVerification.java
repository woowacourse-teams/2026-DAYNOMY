package org.grit.daynomy.emailsubscription.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;

/** 서버의 원문 검증 실행기가 기록할 결과다. AI가 반환한 자체 평가를 전달해서는 안 된다. */
public record CommonStockAnalysisVerification(
    String analysisContentHash, Instant checkedAt, List<SourceCheck> sources) {

  public CommonStockAnalysisVerification {
    if (analysisContentHash == null || !analysisContentHash.matches("[a-f0-9]{64}")) {
      throw new IllegalArgumentException("검증 대상 분석 내용의 해시가 필요합니다.");
    }
    checkedAt = Objects.requireNonNull(checkedAt).truncatedTo(ChronoUnit.MICROS);
    sources = List.copyOf(sources);
  }

  public static CommonStockAnalysisVerification fromOriginals(
      Asset asset,
      CommonStockAnalysis analysis,
      Map<String, PortfolioAnalysisSourceDocument> documents,
      Instant checkedAt) {
    var content = analysis.getContent();
    String claims =
        String.join(
            " ",
            text(content.issueSummary()),
            text(content.reason()),
            text(content.expectedReaction()),
            text(content.outlook()));
    var contexts = new ArrayList<String>();
    for (var source : content.sources()) {
      var document = documents.get(source.url());
      if (document != null) contexts.add(numericContext(document, source.factQuote()));
    }
    var checks = new ArrayList<SourceCheck>();
    for (var source : content.sources()) {
      var document = documents.get(source.url());
      if (document == null) {
        checks.add(new SourceCheck(source.url(), null, false, false, false, false));
        continue;
      }
      String context = document.contextForQuote(source.factQuote());
      String relevance = text(document.title()) + " " + context;
      String name = normalize(asset.getName());
      String code = text(asset.getAssetCode());
      boolean company =
          (!name.isBlank() && normalize(relevance).contains(name))
              || (!code.isBlank()
                  && Pattern.compile("(?<![\\p{L}0-9])" + Pattern.quote(code) + "(?![\\p{L}0-9])")
                      .matcher(relevance)
                      .find());
      boolean quote =
          source.factQuote() != null
              && !source.factQuote().isBlank()
              && !context.isBlank()
              && !document.isAnnouncementOnly();
      boolean dates =
          document.rejectionReason(
                  source.factQuote(), source.publishedOn(), source.occurredOn(), claims)
              == null;
      boolean numbers =
          supportedNumbers(claims, String.join(" ", contexts))
              && supportedNumbers(
                  text(source.excerpt()), numericContext(document, source.factQuote()));
      checks.add(
          new SourceCheck(
              source.url(), originalHash(document.body()), company, dates, numbers, quote));
    }
    return new CommonStockAnalysisVerification(analysis.getContentHash(), checkedAt, checks);
  }

  private static String numericContext(PortfolioAnalysisSourceDocument document, String quote) {
    return document.contextForQuote(quote)
        + " "
        + String.join(" ", document.verifiedWonAmounts(text(quote)).values());
  }

  private static boolean supportedNumbers(String claims, String context) {
    // 수치뿐 아니라 부호와 단위를 함께 비교한다. 10%와 10%p, 조원과 억원은 다르다.
    var pattern =
        Pattern.compile("(?<![0-9.])[-+]?\\d+(?:,\\d{3})*(?:\\.\\d+)?\\s*(?:%p|%|조원|억원|만원|원|달러)?");
    var original = pattern.matcher(context);
    var verified = new HashSet<String>();
    while (original.find()) verified.add(normalize(original.group()).replace(",", ""));
    var generated = pattern.matcher(claims);
    while (generated.find()) {
      if (!verified.contains(normalize(generated.group()).replace(",", ""))) return false;
    }
    return true;
  }

  private static String text(String value) {
    return Objects.toString(value, "");
  }

  private static String normalize(String value) {
    return text(value).replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }

  private static String originalHash(String value) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("원문 해시를 계산하지 못했습니다.", exception);
    }
  }

  public record SourceCheck(
      String url,
      String originalContentHash,
      boolean companyMatched,
      boolean datesMatched,
      boolean numbersMatched,
      boolean quoteMatched) {

    public boolean passed() {
      return originalContentHash != null
          && originalContentHash.matches("[a-f0-9]{64}")
          && companyMatched
          && datesMatched
          && numbersMatched
          && quoteMatched;
    }
  }
}
