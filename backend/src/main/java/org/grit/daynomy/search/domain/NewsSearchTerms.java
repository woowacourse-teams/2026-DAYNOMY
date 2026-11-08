package org.grit.daynomy.search.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public record NewsSearchTerms(List<String> values) {

  public NewsSearchTerms {
    values = List.copyOf(values);
  }

  public static NewsSearchTerms from(String keyword) {
    if (keyword == null) return new NewsSearchTerms(List.of());
    return new NewsSearchTerms(
        Arrays.stream(keyword.strip().split("(?U)\\s+"))
            .filter(term -> !term.isEmpty())
            .map(term -> term.toLowerCase(Locale.ROOT))
            .distinct()
            .toList());
  }
}
