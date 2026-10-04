package org.grit.daynomy.news.domain;

public record ImageSourceInfo(
    String name, String url, String author, String license, String licenseUrl) {

  public ImageSourceInfo(String name, String url) {
    this(name, url, "", "", "");
  }

  public ImageSourceInfo {
    name = normalize(name);
    url = normalize(url);
    author = normalize(author);
    license = normalize(license);
    licenseUrl = normalize(licenseUrl);
  }

  public static ImageSourceInfo empty() {
    return new ImageSourceInfo("", "", "", "", "");
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
