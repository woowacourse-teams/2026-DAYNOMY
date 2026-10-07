package org.grit.daynomy.news.dto;

import java.util.List;

public record WikimediaImageSearchResponse(List<WikimediaImageCandidateResponse> items) {

  public WikimediaImageSearchResponse {
    items = List.copyOf(items);
  }
}
