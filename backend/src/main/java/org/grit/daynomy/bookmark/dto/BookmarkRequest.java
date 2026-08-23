package org.grit.daynomy.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record BookmarkRequest(
    @Schema(description = "북마크할 자산 ID", example = "1")
        @NotNull(message = "자산 ID는 필수입니다.")
        @Positive(message = "자산 ID는 1 이상이어야 합니다.")
        Long targetId) {}
