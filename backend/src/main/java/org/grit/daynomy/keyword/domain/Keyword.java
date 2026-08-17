package org.grit.daynomy.keyword.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class Keyword {

  private final String keyword;
  private final String description;
}
