package org.grit.daynomy.content.dto;

import java.util.List;

public record ContentCollectionSourcesResponse(List<ContentCollectionSourceResponse> sources) {

  public ContentCollectionSourcesResponse {
    sources = List.copyOf(sources);
  }
}
