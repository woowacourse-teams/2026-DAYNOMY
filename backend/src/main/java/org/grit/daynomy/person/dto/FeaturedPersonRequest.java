package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeaturedPersonRequest(
    @Schema(description = "인물 이름", example = "일론 머스크")
        @NotBlank(message = "인물 이름을 입력해주세요.")
        @Size(max = 100, message = "인물 이름은 100자 이하여야 합니다.")
        String name,
    @Schema(description = "X 프로필 URL", example = "https://x.com/elonmusk")
        @Size(max = 2000, message = "X 프로필 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "올바른 X 프로필 URL을 입력해주세요.")
        String xProfileUrl,
    @Schema(description = "Threads 프로필 URL", example = "https://www.threads.net/@example")
        @Size(max = 2000, message = "Threads 프로필 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "올바른 Threads 프로필 URL을 입력해주세요.")
        String threadsProfileUrl,
    @Schema(description = "YouTube 채널 URL", example = "https://www.youtube.com/@example")
        @Size(max = 2000, message = "YouTube 채널 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "올바른 YouTube 채널 URL을 입력해주세요.")
        String youtubeProfileUrl,
    @Schema(description = "Wikimedia Commons 프로필 이미지 선택 정보") @Valid
        FeaturedPersonImageSelectionRequest profileImageSelection) {}
