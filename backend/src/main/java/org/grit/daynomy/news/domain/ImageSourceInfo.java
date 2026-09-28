package org.grit.daynomy.news.domain;

public record ImageSourceInfo(String name, String url) {

  public static ImageSourceInfo empty() {
    return new ImageSourceInfo("", "");
  }
}
