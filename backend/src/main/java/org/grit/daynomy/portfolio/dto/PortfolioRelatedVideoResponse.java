package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.external.youtube.YouTubeVideoCandidate;

public record PortfolioRelatedVideoResponse(
    @Schema(description = "영상 제목") String title,
    @Schema(description = "YouTube 영상 링크") String url,
    @Schema(description = "채널명") String channelTitle,
    @Schema(description = "영상 게시 시각") String publishedAt,
    @Schema(description = "영상 썸네일 링크") String thumbnailUrl) {

  public static PortfolioRelatedVideoResponse from(YouTubeVideoCandidate candidate) {
    return new PortfolioRelatedVideoResponse(
        candidate.title(),
        candidate.url(),
        candidate.channelTitle(),
        candidate.publishedAt(),
        candidate.thumbnailUrl());
  }
}
