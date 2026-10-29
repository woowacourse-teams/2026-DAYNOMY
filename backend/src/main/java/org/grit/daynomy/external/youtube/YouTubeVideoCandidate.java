package org.grit.daynomy.external.youtube;

public record YouTubeVideoCandidate(
    String title,
    String url,
    String channelTitle,
    String publishedAt,
    String thumbnailUrl,
    String description) {

  public YouTubeVideoCandidate(
      String title, String url, String channelTitle, String publishedAt, String thumbnailUrl) {
    this(title, url, channelTitle, publishedAt, thumbnailUrl, "");
  }
}
