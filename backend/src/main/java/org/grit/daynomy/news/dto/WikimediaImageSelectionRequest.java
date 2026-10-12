package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record WikimediaImageSelectionRequest(
    @Schema(description = "Wikimedia Commons 파일 제목", example = "File:Seoul skyline.jpg")
        @NotBlank(message = "Wikimedia Commons 이미지 파일 제목은 필수입니다.")
        String title) {}
