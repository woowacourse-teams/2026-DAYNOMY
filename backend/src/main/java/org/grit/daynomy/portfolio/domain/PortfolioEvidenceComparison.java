package org.grit.daynomy.portfolio.domain;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/** 문장 유사도로 새 사건을 추측하지 않고 확인 가능한 날짜·사실만 비교한다. */
public final class PortfolioEvidenceComparison {
  private PortfolioEvidenceComparison() {}

  public enum Result {
    SAME,
    NEW_EVIDENCE,
    UNCONFIRMED
  }

  public record Fact(String url, LocalDate occurredOn, String excerpt, String factQuote) {
    public Fact(String url, LocalDate occurredOn, String excerpt) {
      this(url, occurredOn, excerpt, null);
    }
  }

  public static Result compare(List<Fact> previous, List<Fact> current) {
    if (current.isEmpty()) {
      return Result.UNCONFIRMED;
    }
    if (current.stream().anyMatch(fact -> normalized(fact.excerpt()).isEmpty())) {
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
    if (latest == null
        || added.stream()
            .anyMatch(fact -> fact.occurredOn() == null || !fact.occurredOn().isAfter(latest))) {
      return Result.UNCONFIRMED;
    }
    return Result.NEW_EVIDENCE;
  }

  private static boolean sameFact(Fact left, Fact right) {
    if (!Objects.equals(left.occurredOn(), right.occurredOn())
        || normalized(left.excerpt()).isEmpty()
        || !sameText(left, right)) {
      return false;
    }
    // 사건일이 확인된 같은 사실의 재보도는 URL이 달라도 새 사건이 아니다.
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
