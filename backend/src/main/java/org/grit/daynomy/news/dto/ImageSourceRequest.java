package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import org.grit.daynomy.news.domain.ImageSourceInfo;

public record ImageSourceRequest(
    @Schema(description = "이미지 출처명", example = "Unsplash") @NotBlank(message = "이미지 출처명은 필수입니다.")
        String name,
    @Schema(description = "이미지 출처 URL", example = "https://unsplash.com/photos/example")
        @NotBlank(message = "이미지 출처 URL은 필수입니다.")
        String url) {

  public ImageSourceInfo toDomain() {
    return new ImageSourceInfo(name, url);
  }
}
