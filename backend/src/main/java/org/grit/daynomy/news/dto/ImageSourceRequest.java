package org.grit.daynomy.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import org.grit.daynomy.news.domain.ImageSourceInfo;

public record ImageSourceRequest(
    @Schema(description = "이미지 출처명", example = "Unsplash") String name,
    @Schema(description = "이미지 출처 URL", example = "https://unsplash.com/photos/example")
        String url) {

  @JsonIgnore
  @AssertTrue(message = "이미지 출처명과 URL은 모두 입력하거나 모두 비워야 합니다.")
  public boolean isCompleteOrEmpty() {
    boolean nameEmpty = name == null || name.isBlank();
    boolean urlEmpty = url == null || url.isBlank();
    return nameEmpty == urlEmpty;
  }

  public ImageSourceInfo toDomain() {
    return new ImageSourceInfo(name, url);
  }
}
