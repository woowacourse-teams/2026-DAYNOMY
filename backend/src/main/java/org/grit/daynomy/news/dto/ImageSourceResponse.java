package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.news.domain.ImageSourceInfo;

public record ImageSourceResponse(
    @Schema(description = "이미지 출처명", example = "Unsplash") String name,
    @Schema(description = "이미지 출처 URL", example = "https://unsplash.com/photos/example")
        String url) {

  public static ImageSourceResponse from(ImageSourceInfo source) {
    if (source == null) {
      return empty();
    }
    return new ImageSourceResponse(source.name(), source.url());
  }

  public static ImageSourceResponse empty() {
    return new ImageSourceResponse("", "");
  }
}
