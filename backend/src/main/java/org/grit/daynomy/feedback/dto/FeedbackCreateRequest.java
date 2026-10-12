package org.grit.daynomy.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeedbackCreateRequest(
    @Schema(description = "피드백 내용", example = "포트폴리오 화면에 수익률 정렬 기능이 필요해요.")
        @NotBlank(message = "피드백 내용을 입력해주세요.")
        @Size(max = 1000, message = "피드백은 1000자 이하여야 합니다.")
        String content,
    @Schema(description = "피드백을 작성한 화면 경로", example = "/portfolio")
        @NotBlank(message = "작성 화면 경로가 필요합니다.")
        @Size(max = 2048, message = "작성 화면 경로는 2048자 이하여야 합니다.")
        String pagePath) {}
