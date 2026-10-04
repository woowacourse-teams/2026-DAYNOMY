package org.grit.daynomy.news.domain;

public record ImageSourceInfo(
    String name,
    String url,
    String author,
    String license,
    String licenseUrl,
    ImageSourceType type) {

  public ImageSourceInfo(
      String name, String url, String author, String license, String licenseUrl) {
    this(name, url, author, license, licenseUrl, ImageSourceType.MANUAL);
  }

  public ImageSourceInfo {
    name = normalize(name);
    url = normalize(url);
    author = normalize(author);
    license = normalize(license);
    licenseUrl = normalize(licenseUrl);
    type = type == null ? ImageSourceType.NONE : type;
  }

  public static ImageSourceInfo empty() {
    return new ImageSourceInfo("", "", "", "", "", ImageSourceType.NONE);
  }

  public static ImageSourceInfo aiGenerated() {
    return new ImageSourceInfo("", "", "", "", "", ImageSourceType.AI_GENERATED);
  }

  public static ImageSourceInfo manual() {
    return new ImageSourceInfo("", "", "", "", "", ImageSourceType.MANUAL);
  }

  public static ImageSourceInfo wikimedia(
      String name, String url, String author, String license, String licenseUrl) {
    return new ImageSourceInfo(name, url, author, license, licenseUrl, ImageSourceType.WIKIMEDIA);
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
