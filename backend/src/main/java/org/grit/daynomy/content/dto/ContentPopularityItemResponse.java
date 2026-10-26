package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.grit.daynomy.content.domain.ContentType;

public record ContentPopularityItemResponse(
    @Schema(description = "콘텐츠 유형", example = "NEWS") ContentType contentType,
    @Schema(description = "콘텐츠 ID", example = "1") Long contentId,
    @Schema(description = "인기 점수", example = "15") long score,
    @Schema(description = "마지막 반응 시각", example = "2026-10-23T09:00:00Z")
        Instant lastInteractedAt) {}
