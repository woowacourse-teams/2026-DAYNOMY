package org.grit.daynomy.portfolio.domain;

import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Pattern;

/** 원문의 인용문과 날짜를 대조한다. 의미 전체의 진위까지 보장하는 검증은 아니다. */
public record PortfolioAnalysisSourceDocument(String title, String body, LocalDate publishedOn) {
  private static final Pattern YEAR =
      Pattern.compile(
          "(?<![0-9])20[0-9]{2}(?=\\s*(년|회계연도|[./-][0-9]{1,2}[./-][0-9]{1,2}(?![0-9])|[1-4]\\s*분기|[Qq][1-4]))");

  public boolean isAnnouncementOnly() {
    String text = normalize(title + " " + body).replace(" ", "");
    return (normalize(body).startsWith("please wait for a moment.")
            && text.contains("maindocumentsselect"))
        || text.contains("결산실적공시예고")
        || text.contains("earningsreleasedateannouncement")
        || (text.contains("pleasefollowthelinkbelowtosee")
            && !Pattern.compile("(?:revenue|operatingprofit|매출|영업이익)[^.!?]{0,80}[0-9]")
                .matcher(text)
                .find());
  }

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
    String bodyContext = contextForQuote(quote);
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
    String context = normalize(title) + " " + bodyContext;
    var years = YEAR.matcher(normalize(claims));
    while (years.find()) {
      if (!bodyContext.contains(years.group()) && !hasAbbreviatedYear(context, years.group()))
        return "PERIOD_UNVERIFIED";
    }
    String normalizedClaims = normalize(claims);
    for (int quarter = 1; quarter <= 4; quarter++) {
      if (hasQuarter(normalizedClaims, quarter) && !hasQuarter(context, quarter))
        return "PERIOD_UNVERIFIED";
    }
    if (normalizedClaims.contains("상반기")
        && !(context.contains("상반기")
            || Pattern.compile("first[\\s\\p{Pd}]+half|first[\\s\\p{Pd}]+six[\\s\\p{Pd}]+months")
                .matcher(context)
                .find()
            || context.contains("h1"))) return "PERIOD_UNVERIFIED";
    if (normalizedClaims.contains("하반기")
        && !(context.contains("하반기")
            || Pattern.compile("second[\\s\\p{Pd}]+half|last[\\s\\p{Pd}]+six[\\s\\p{Pd}]+months")
                .matcher(context)
                .find()
            || context.contains("h2"))) return "PERIOD_UNVERIFIED";
    return null;
  }

  public LocalDate confirmedOccurrence(String quote, LocalDate candidate) {
    if (candidate == null || candidate.isAfter(LocalDate.now(java.time.ZoneId.of("Asia/Seoul"))))
      return null;
    return containsDate(contextForQuote(quote), candidate) ? candidate : null;
  }

  public java.util.Set<String> billionWonAmounts(String quote) {
    String text = normalize(body);
    int position = text.indexOf(normalize(quote));
    if (position < 0) return java.util.Set.of();
    var explicit =
        Pattern.compile(
                "(?i)(?:KRW\\s+([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+(?:billion|bn)|([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+(?:billion|bn)\\s+(?:won|KRW))")
            .matcher(quote);
    var values = new java.util.HashSet<String>();
    while (explicit.find()) {
      String value = explicit.group(1) == null ? explicit.group(2) : explicit.group(1);
      values.add(
          new java.math.BigDecimal(value.replace(",", "")).stripTrailingZeros().toPlainString());
    }
    if (!Pattern.compile(
            "(?i)^\\s*(total\\s+revenue|operating\\s+profit|net\\s+(income|profit))\\s+[-0-9]")
        .matcher(quote)
        .find()) return values;
    var units =
        Pattern.compile("\\(\\s*in\\s+([^)]{1,40})\\)").matcher(text.substring(0, position));
    String unit = "";
    while (units.find()) unit = units.group(1);
    if (!unit.matches("(?:bn|billion)\\s+krw|krw\\s+(?:bn|billion)")) return java.util.Set.of();
    var numbers = Pattern.compile("[0-9]+(?:\\.[0-9]+)?").matcher(quote.replace(",", ""));
    while (numbers.find()) {
      if (quote.replace(",", "").substring(numbers.end()).stripLeading().startsWith("%")) continue;
      values.add(new java.math.BigDecimal(numbers.group()).stripTrailingZeros().toPlainString());
    }
    return values;
  }

  public java.util.Map<String, String> verifiedWonAmounts(String quote) {
    if (!normalize(body).contains(normalize(quote))) return java.util.Map.of();
    var amounts =
        Pattern.compile(
                "(?i)(?:KRW\\s+([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+(million|billion|trillion)|([0-9][0-9,]*(?:\\.[0-9]+)?)\\s+(million|billion|trillion)\\s+(?:won|KRW))")
            .matcher(quote);
    var converted = new java.util.LinkedHashMap<String, String>();
    while (amounts.find()) {
      String number = amounts.group(1) == null ? amounts.group(3) : amounts.group(1);
      String unit =
          (amounts.group(2) == null ? amounts.group(4) : amounts.group(2)).toLowerCase(Locale.ROOT);
      int power =
          switch (unit) {
            case "million" -> 6;
            case "billion" -> 9;
            default -> 12;
          };
      converted.put(
          amounts.group(),
          new java.math.BigDecimal(number.replace(",", ""))
                  .scaleByPowerOfTen(power - 8)
                  .stripTrailingZeros()
                  .toPlainString()
              + "억원");
    }
    for (String value : billionWonAmounts(quote))
      converted.putIfAbsent(
          value + " billion KRW",
          new java.math.BigDecimal(value)
                  .multiply(java.math.BigDecimal.TEN)
                  .stripTrailingZeros()
                  .toPlainString()
              + "억원");
    return converted;
  }

  public String normalizeAmountUnit(String quote, String excerpt) {
    return normalizeAmountUnits(billionWonAmounts(quote), excerpt);
  }

  public static String normalizeAmountUnits(java.util.Set<String> values, String excerpt) {
    var amounts =
        Pattern.compile("(?<![0-9.,조억])([0-9][0-9,]*(?:\\.[0-9]+)?)\\s*억원").matcher(excerpt);
    StringBuilder normalized = new StringBuilder();
    while (amounts.find()) {
      var number = new java.math.BigDecimal(amounts.group(1).replace(",", ""));
      boolean alreadyConverted =
          values.contains(
              number.divide(java.math.BigDecimal.TEN).stripTrailingZeros().toPlainString());
      if (!excerpt.substring(0, amounts.start()).matches("(?s).*조\\s*$")
          && !alreadyConverted
          && values.contains(number.stripTrailingZeros().toPlainString())) {
        amounts.appendReplacement(
            normalized,
            number.multiply(java.math.BigDecimal.TEN).stripTrailingZeros().toPlainString() + "억원");
      } else
        amounts.appendReplacement(
            normalized, java.util.regex.Matcher.quoteReplacement(amounts.group()));
    }
    amounts.appendTail(normalized);
    return normalized.toString();
  }

  public String contextForQuote(String quote) {
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
    String english =
        new String[] {"first", "second", "third", "fourth"}[quarter - 1] + "[\\s\\p{Pd}]+quarter";
    return Pattern.compile(english).matcher(text).find()
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

  private static boolean hasAbbreviatedYear(String text, String year) {
    String abbreviated = year.substring(2);
    return Pattern.compile(
            "(?<![a-z0-9])(?:"
                + abbreviated
                + "\\s*년|[1-4]\\s*q"
                + abbreviated
                + "(?![0-9])|q[1-4]"
                + abbreviated
                + "(?![0-9]))")
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
