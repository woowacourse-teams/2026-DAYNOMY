package org.grit.daynomy.content.dto;

import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.external.openai.SocialPostCandidate;

public record SocialPostResponse(
    ContentSourceType sourceType, String title, String url, String publishedDate) {

  public static SocialPostResponse from(SocialPostCandidate candidate) {
    return new SocialPostResponse(
        ContentSourceType.valueOf(candidate.platform().name()),
        candidate.title(),
        candidate.url(),
        candidate.publishedDate());
  }
}
