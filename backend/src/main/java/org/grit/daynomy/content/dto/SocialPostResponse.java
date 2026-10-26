package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.external.openai.SocialPostCandidate;

public record SocialPostResponse(
    @Schema(description = "게시물 출처", example = "X") ContentSourceType sourceType,
    @Schema(description = "게시물 제목", example = "시장 전망에 대한 의견") String title,
    @Schema(description = "게시물 URL", example = "https://x.com/example/status/1") String url,
    @Schema(description = "원문 게시일", example = "2026-10-22") String publishedDate) {

  public static SocialPostResponse from(SocialPostCandidate candidate) {
    return new SocialPostResponse(
        ContentSourceType.valueOf(candidate.platform().name()),
        candidate.title(),
        candidate.url(),
        candidate.publishedDate());
  }
}
