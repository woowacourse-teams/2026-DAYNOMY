package org.grit.daynomy.portfolio.domain;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** 검증된 원문 사실의 추가·정정을 비교한다. 사건일의 선후만으로 갱신을 제한하지 않는다. */
public final class PortfolioEvidenceComparison {
  private PortfolioEvidenceComparison() {}

  public enum Result {
    SAME,
    NEW_EVIDENCE,
    UNCONFIRMED
  }

  public record Fact(
      String url, LocalDate occurredOn, String excerpt, String factQuote, LocalDate publishedOn) {
    public Fact(String url, LocalDate occurredOn, String excerpt, String factQuote) {
      this(url, occurredOn, excerpt, factQuote, null);
    }

    public Fact(String url, LocalDate occurredOn, String excerpt) {
      this(url, occurredOn, excerpt, null, null);
    }
  }

  public static Result compare(List<Fact> previous, List<Fact> current) {
    if (current.isEmpty()) {
      return Result.UNCONFIRMED;
    }
    LocalDate today = LocalDate.now(java.time.ZoneId.of("Asia/Seoul"));
    if (current.stream()
        .anyMatch(
            fact ->
                normalized(fact.excerpt()).isEmpty()
                    || (fact.occurredOn() != null && fact.occurredOn().isAfter(today))
                    || (fact.publishedOn() != null && fact.publishedOn().isAfter(today)))) {
      return Result.UNCONFIRMED;
    }
    if (previous.isEmpty()) {
      return Result.NEW_EVIDENCE;
    }
    List<Fact> added =
        current.stream()
            .filter(fact -> previous.stream().noneMatch(old -> sameFact(old, fact)))
            .toList();
    if (added.isEmpty()) {
      return previous.stream()
              .allMatch(old -> current.stream().anyMatch(fact -> sameFact(old, fact)))
          ? Result.SAME
          : Result.UNCONFIRMED;
    }
    LocalDate latest =
        previous.stream()
            .map(Fact::occurredOn)
            .filter(Objects::nonNull)
            .max(LocalDate::compareTo)
            .orElse(null);
    if (added.stream()
        .anyMatch(
            fact -> {
              if (!normalized(fact.factQuote()).isEmpty())
                return fact.occurredOn() == null && fact.publishedOn() == null;
              // 원문 발췌가 없는 과거 형식의 근거는 기존의 날짜 확인 기준을 유지한다.
              return latest == null
                  || fact.occurredOn() == null
                  || !fact.occurredOn().isAfter(latest);
            })) {
      return Result.UNCONFIRMED;
    }
    return Result.NEW_EVIDENCE;
  }

  private static boolean sameFact(Fact left, Fact right) {
    if (normalized(left.excerpt()).isEmpty() || !sameText(left, right)) {
      return false;
    }
    if (left.occurredOn() != null && right.occurredOn() != null)
      return left.occurredOn().equals(right.occurredOn());
    // 같은 원문 사실의 재보도·발표일 변경·사건일 보완은 새 사실이 아니다.
    if (!normalized(left.factQuote()).isEmpty() && !normalized(right.factQuote()).isEmpty())
      return true;
    if (!Objects.equals(left.occurredOn(), right.occurredOn())) return false;
    return left.occurredOn() != null
        || normalizedUrl(left.url()).equals(normalizedUrl(right.url()));
  }

  private static boolean sameText(Fact left, Fact right) {
    // 요약 문체가 바뀌어도 원문 사실은 동일하게 비교한다. 양쪽에 발췌가 있으면 이를 우선한다.
    if (!normalized(left.factQuote()).isEmpty() && !normalized(right.factQuote()).isEmpty()) {
      return normalized(left.factQuote()).equals(normalized(right.factQuote()));
    }
    return normalized(left.excerpt()).equals(normalized(right.excerpt()));
  }

  private static String normalized(String value) {
    return value == null ? "" : value.strip().replaceAll("\\s+", " ").replaceAll("[.。]+$", "");
  }

  private static String normalizedUrl(String url) {
    URI uri = URI.create(url);
    String query = uri.getRawQuery();
    if (query != null) {
      query =
          java.util.Arrays.stream(query.split("&"))
              .filter(part -> !part.toLowerCase(java.util.Locale.ROOT).startsWith("utm_"))
              .sorted()
              .collect(java.util.stream.Collectors.joining("&"));
    }
    return uri.getScheme()
        + "://"
        + uri.getRawAuthority()
        + uri.getRawPath()
        + (query == null || query.isEmpty() ? "" : "?" + query);
  }
}
