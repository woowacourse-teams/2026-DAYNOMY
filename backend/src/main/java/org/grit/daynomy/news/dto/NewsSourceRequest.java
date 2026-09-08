package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record NewsSourceRequest(
    @Schema(description = "출처명", example = "DART 전자공시시스템") @NotBlank(message = "출처명은 필수입니다.")
        String name,
    @Schema(description = "출처 URL", example = "https://example.com/news/1")
        @NotBlank(message = "출처 URL은 필수입니다.")
        String url) {}
