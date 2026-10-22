package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.grit.daynomy.content.domain.ContentInteractionType;
import org.grit.daynomy.content.domain.ContentType;

public record ContentInteractionRequest(
    @Schema(
            description = "클라이언트가 생성한 중복 방지용 이벤트 ID",
            example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull
        UUID eventId,
    @Schema(description = "콘텐츠 유형", example = "NEWS") @NotNull ContentType contentType,
    @Schema(description = "콘텐츠 ID", example = "1") @NotNull @Positive Long contentId,
    @Schema(description = "사용자 반응 유형", example = "OPEN") @NotNull
        ContentInteractionType interactionType) {}
