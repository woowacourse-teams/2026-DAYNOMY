package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.news.domain.ImageSourceInfo;

public record ImageSourceResponse(
    @Schema(description = "이미지 출처명", example = "Unsplash") String name,
    @Schema(description = "이미지 출처 URL", example = "https://unsplash.com/photos/example")
        String url,
    @Schema(description = "이미지 저작자", example = "Jane Doe") String author,
    @Schema(description = "이미지 라이선스", example = "CC BY 4.0") String license,
    @Schema(description = "이미지 라이선스 URL", example = "https://creativecommons.org/licenses/by/4.0/")
        String licenseUrl) {

  public ImageSourceResponse(String name, String url) {
    this(name, url, "", "", "");
  }

  public static ImageSourceResponse from(ImageSourceInfo source) {
    if (source == null) {
      return empty();
    }
    return new ImageSourceResponse(
        source.name(), source.url(), source.author(), source.license(), source.licenseUrl());
  }

  public static ImageSourceResponse empty() {
    return new ImageSourceResponse("", "", "", "", "");
  }
}
