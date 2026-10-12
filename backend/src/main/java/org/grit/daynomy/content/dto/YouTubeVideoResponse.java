package org.grit.daynomy.content.dto;

import org.grit.daynomy.external.youtube.YouTubeVideoCandidate;

public record YouTubeVideoResponse(
    String title, String url, String channelTitle, String publishedAt, String thumbnailUrl) {

  public static YouTubeVideoResponse from(YouTubeVideoCandidate candidate) {
    return new YouTubeVideoResponse(
        candidate.title(),
        candidate.url(),
        candidate.channelTitle(),
        candidate.publishedAt(),
        candidate.thumbnailUrl());
  }
}
