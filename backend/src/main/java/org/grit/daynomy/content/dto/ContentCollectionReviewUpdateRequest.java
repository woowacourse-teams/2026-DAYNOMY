package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContentCollectionReviewUpdateRequest(
    @Schema(description = "한국어 제목", example = "삼성전자, 신제품 공개")
        @NotBlank(message = "제목은 필수입니다.")
        @Size(max = 255, message = "제목은 255자 이하여야 합니다.")
        String title,
    @Schema(description = "한국어 설명", example = "삼성전자가 새로운 제품을 공개했다.")
        @NotNull(message = "설명은 필수입니다.")
        String description) {}
