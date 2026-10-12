package org.grit.daynomy.external.openai;

public record SocialPostCandidate(
    Platform platform, String title, String url, String publishedDate) {

  public enum Platform {
    THREADS,
    X
  }
}
