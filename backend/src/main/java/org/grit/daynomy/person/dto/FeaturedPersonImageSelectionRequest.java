package org.grit.daynomy.person.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record FeaturedPersonImageSelectionRequest(
    @NotBlank(message = "Wikimedia Commons 이미지 파일 제목은 필수입니다.")
        @Size(max = 500, message = "Wikimedia Commons 이미지 파일 제목은 500자 이하여야 합니다.")
        String title) {}
