package org.grit.daynomy.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import org.grit.daynomy.feedback.domain.FeedbackStatus;

public record FeedbackStatusUpdateRequest(
    @Schema(description = "피드백 처리 상태", example = "RESOLVED")
        @NotNull(message = "피드백 상태를 선택해주세요.")
        FeedbackStatus status) {}
