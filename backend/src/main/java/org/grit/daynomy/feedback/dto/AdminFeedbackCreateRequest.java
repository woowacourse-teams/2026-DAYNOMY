package org.grit.daynomy.feedback.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.grit.daynomy.feedback.domain.FeedbackCategory;

public record AdminFeedbackCreateRequest(
    @Schema(description = "피드백 유형", example = "INCONVENIENCE")
        @NotNull(message = "피드백 유형을 선택해주세요.")
        FeedbackCategory category,
    @Schema(description = "피드백 내용", example = "뉴스 등록 화면에서 저장 결과를 확인하기 어려워요.")
        @NotBlank(message = "피드백 내용을 입력해주세요.")
        @Size(max = 1000, message = "피드백은 1000자 이하여야 합니다.")
        String content,
    @Schema(description = "피드백을 작성한 관리자 화면 경로", example = "/admin/news/new")
        @NotBlank(message = "작성 화면 경로가 필요합니다.")
        @Size(max = 2048, message = "작성 화면 경로는 2048자 이하여야 합니다.")
        String pagePath) {}
