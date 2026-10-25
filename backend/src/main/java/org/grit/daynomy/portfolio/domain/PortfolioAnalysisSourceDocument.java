package org.grit.daynomy.portfolio.domain;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/** 원문의 인용문과 날짜를 대조한다. 의미 전체의 진위까지 보장하는 검증은 아니다. */
public record PortfolioAnalysisSourceDocument(String title, String body, LocalDate publishedOn) {
  private static final Pattern YEAR = Pattern.compile("(?<![0-9])20[0-9]{2}(?![0-9])");

  public String rejectionReason(
      String quote, LocalDate publication, LocalDate occurrence, String claims) {
    if ((publishedOn != null
            && publishedOn.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))))
        || (publication != null && !publication.equals(publishedOn)))
      return "PUBLICATION_DATE_UNVERIFIED";
    if (quote == null || quote.isBlank()) return "QUOTE_NOT_IN_ARTICLE";
    String text = normalize(body);
    String fact = normalize(quote);
    int position = text.indexOf(fact);
    if (position < 0) return "QUOTE_NOT_IN_ARTICLE";
    String bodyContext = quoteContext(quote);
    var explicitDates = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}").matcher(claims);
    while (explicitDates.find()) {
      try {
        if (!containsDate(bodyContext, LocalDate.parse(explicitDates.group())))
          return "EVENT_DATE_UNVERIFIED";
      } catch (java.time.DateTimeException invalid) {
        return "EVENT_DATE_UNVERIFIED";
      }
    }
    if (occurrence != null
        && (occurrence.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Seoul")))
            || !containsDate(bodyContext, occurrence))) return "EVENT_DATE_UNVERIFIED";
    var years = YEAR.matcher(normalize(claims));
    while (years.find()) {
      if (!bodyContext.contains(years.group())) return "PERIOD_UNVERIFIED";
    }
    String context = normalize(title) + " " + bodyContext;
    String normalizedClaims = normalize(claims);
    for (int quarter = 1; quarter <= 4; quarter++) {
      if (hasQuarter(normalizedClaims, quarter) && !hasQuarter(context, quarter))
        return "PERIOD_UNVERIFIED";
    }
    if (normalizedClaims.contains("상반기")
        && !(context.contains("상반기") || context.contains("first half") || context.contains("h1")))
      return "PERIOD_UNVERIFIED";
    if (normalizedClaims.contains("하반기")
        && !(context.contains("하반기") || context.contains("second half") || context.contains("h2")))
      return "PERIOD_UNVERIFIED";
    return null;
  }

  public LocalDate confirmedOccurrence(String quote, LocalDate candidate) {
    if (candidate == null || candidate.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))))
      return null;
    return containsDate(quoteContext(quote), candidate) ? candidate : null;
  }

  private String quoteContext(String quote) {
    String text = normalize(body);
    String fact = normalize(quote);
    int position = text.indexOf(fact);
    if (position < 0) return "";
    return text.substring(0, Math.min(text.length(), 600))
        + " "
        + text.substring(
            Math.max(0, position - 300), Math.min(text.length(), position + fact.length() + 300));
  }

  private static boolean hasQuarter(String text, int quarter) {
    String english = new String[] {"first", "second", "third", "fourth"}[quarter - 1] + " quarter";
    return text.contains(english)
        || Pattern.compile(
                "(?<![a-z0-9])(?:q\\s*"
                    + quarter
                    + "|"
                    + quarter
                    + "\\s*q(?:[0-9]{2})?|"
                    + quarter
                    + "\\s*분기)(?![a-z0-9])")
            .matcher(text)
            .find();
  }

  private static boolean containsDate(String text, LocalDate date) {
    return text.contains(date.toString())
        || text.contains(
            date.getYear() + "년 " + date.getMonthValue() + "월 " + date.getDayOfMonth() + "일")
        || text.contains(
            date.getYear() + "년" + date.getMonthValue() + "월" + date.getDayOfMonth() + "일")
        || text.contains(
            date.format(DateTimeFormatter.ofPattern("MMMM d, uuuu", Locale.ENGLISH))
                .toLowerCase(Locale.ROOT))
        || text.contains(
            date.format(DateTimeFormatter.ofPattern("d MMMM uuuu", Locale.ENGLISH))
                .toLowerCase(Locale.ROOT));
  }

  private static String normalize(String text) {
    if (text == null) return "";
    return Normalizer.normalize(text, Normalizer.Form.NFC)
        .replaceAll("[\\p{Z}\\s\\u200B\\uFEFF]+", " ")
        .replaceAll("(?<=[가-힣]) (은|는|이|가|을|를|의|와|과|에|로)(?= |[.,]|$)", "$1")
        .trim()
        .toLowerCase(Locale.ROOT);
  }
}
