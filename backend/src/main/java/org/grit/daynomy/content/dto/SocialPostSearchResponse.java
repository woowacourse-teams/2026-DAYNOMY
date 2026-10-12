package org.grit.daynomy.content.dto;

import java.util.List;

public record SocialPostSearchResponse(List<SocialPostResponse> items) {

  public SocialPostSearchResponse {
    items = items == null ? List.of() : List.copyOf(items);
  }
}
