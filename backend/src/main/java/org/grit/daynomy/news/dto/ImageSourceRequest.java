package org.grit.daynomy.news.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import org.grit.daynomy.news.domain.ImageSourceInfo;

public record ImageSourceRequest(
    @Schema(description = "이미지 출처명", example = "Unsplash") String name,
    @Schema(description = "이미지 출처 URL", example = "https://unsplash.com/photos/example")
        String url,
    @Schema(description = "이미지 저작자", example = "Jane Doe") String author,
    @Schema(description = "이미지 라이선스", example = "CC BY 4.0") String license,
    @Schema(description = "이미지 라이선스 URL", example = "https://creativecommons.org/licenses/by/4.0/")
        String licenseUrl) {

  public ImageSourceRequest {
    name = normalize(name);
    url = normalize(url);
    author = normalize(author);
    license = normalize(license);
    licenseUrl = normalize(licenseUrl);
  }

  public ImageSourceRequest(String name, String url) {
    this(name, url, "", "", "");
  }

  @JsonIgnore
  @AssertTrue(message = "이미지 출처명과 URL은 모두 입력하거나 모두 비워야 합니다.")
  public boolean isCompleteOrEmpty() {
    boolean nameEmpty = name == null || name.isBlank();
    boolean urlEmpty = url == null || url.isBlank();
    return nameEmpty == urlEmpty;
  }

  public ImageSourceInfo toDomain() {
    return new ImageSourceInfo(name, url, author, license, licenseUrl);
  }

  private static String normalize(String value) {
    return value == null ? "" : value;
  }
}
