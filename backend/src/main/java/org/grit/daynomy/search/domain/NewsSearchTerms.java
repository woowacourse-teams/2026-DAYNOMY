package org.grit.daynomy.search.domain;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public record NewsSearchTerms(List<String> values) {

  public NewsSearchTerms {
    values = List.copyOf(values);
  }

  public static NewsSearchTerms from(String keyword) {
    return new NewsSearchTerms(
        Arrays.stream(keyword.strip().split("(?U)\\s+"))
            .filter(term -> !term.isEmpty())
            .map(term -> term.toLowerCase(Locale.ROOT))
            .distinct()
            .toList());
  }
}
