package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeaturedPersonImageSelectionRequest(
    @Schema(description = "Wikimedia Commons 파일 제목", example = "File:Elon Musk 2023.jpg")
        @NotBlank(message = "Wikimedia Commons 이미지 파일 제목은 필수입니다.")
        @Size(max = 500, message = "Wikimedia Commons 이미지 파일 제목은 500자 이하여야 합니다.")
        String title) {}
