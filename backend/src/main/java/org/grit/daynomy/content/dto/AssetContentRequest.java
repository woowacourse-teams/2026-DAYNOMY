package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.grit.daynomy.content.domain.ContentSourceType;

public record AssetContentRequest(
    @NotNull(message = "출처를 선택해주세요.") ContentSourceType sourceType,
    @NotBlank(message = "제목을 입력해주세요.") @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
        String title,
    @NotBlank(message = "URL을 입력해주세요.")
        @Size(max = 2000, message = "URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "http 또는 https URL을 입력해주세요.")
        String url,
    @Size(max = 2000, message = "이미지 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "http 또는 https 이미지 URL을 입력해주세요.")
        String imageUrl,
    @Schema(description = "원문 게시일. 확인하지 못한 경우 생략 또는 null", example = "2020-04-05")
        LocalDate publishedDate) {}
