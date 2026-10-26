package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.external.youtube.YouTubeVideoCandidate;

public record YouTubeVideoResponse(
    @Schema(description = "영상 제목", example = "삼성전자 실적 분석") String title,
    @Schema(description = "영상 URL", example = "https://www.youtube.com/watch?v=example") String url,
    @Schema(description = "채널 이름", example = "DAYNOMY") String channelTitle,
    @Schema(description = "게시 시각", example = "2026-10-23T09:00:00Z") String publishedAt,
    @Schema(description = "썸네일 URL", example = "https://i.ytimg.com/vi/example/default.jpg")
        String thumbnailUrl) {

  public static YouTubeVideoResponse from(YouTubeVideoCandidate candidate) {
    return new YouTubeVideoResponse(
        candidate.title(),
        candidate.url(),
        candidate.channelTitle(),
        candidate.publishedAt(),
        candidate.thumbnailUrl());
  }
}
