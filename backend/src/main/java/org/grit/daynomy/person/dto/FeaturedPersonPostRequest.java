package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.grit.daynomy.content.domain.ContentSourceType;

public record FeaturedPersonPostRequest(
    @Schema(description = "게시물 출처", example = "X") @NotNull(message = "출처를 선택해주세요.")
        ContentSourceType sourceType,
    @Schema(description = "게시물 제목", example = "시장 전망에 대한 의견")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
        String title,
    @Schema(description = "게시물 URL", example = "https://x.com/example/status/1")
        @NotBlank(message = "게시물 URL을 입력해주세요.")
        @Size(max = 2000, message = "게시물 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "올바른 게시물 URL을 입력해주세요.")
        String url,
    @Schema(description = "원문 게시 시각", example = "2026-10-23T09:00:00Z") Instant publishedAt) {}
