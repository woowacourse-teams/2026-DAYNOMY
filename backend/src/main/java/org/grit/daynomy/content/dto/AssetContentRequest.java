package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.grit.daynomy.content.domain.ContentSourceType;

public record AssetContentRequest(
    @Schema(description = "콘텐츠 출처", example = "YOUTUBE") @NotNull(message = "출처를 선택해주세요.")
        ContentSourceType sourceType,
    @Schema(description = "콘텐츠 제목", example = "삼성전자 3분기 실적 분석")
        @NotBlank(message = "제목을 입력해주세요.")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
        String title,
    @Schema(description = "원문 URL", example = "https://example.com/contents/1")
        @NotBlank(message = "URL을 입력해주세요.")
        @Size(max = 2000, message = "URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "http 또는 https URL을 입력해주세요.")
        String url,
    @Schema(description = "대표 이미지 URL", example = "https://example.com/images/1.png")
        @Size(max = 2000, message = "이미지 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "http 또는 https 이미지 URL을 입력해주세요.")
        String imageUrl,
    @Schema(description = "원문 게시일. 생략 또는 null이면 서울 기준 등록일을 사용", example = "2020-04-05")
        LocalDate publishedDate) {}
