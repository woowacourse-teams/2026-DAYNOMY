package org.grit.daynomy.person.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record FeaturedPersonRequest(
    @NotBlank(message = "인물 이름을 입력해주세요.") @Size(max = 100, message = "인물 이름은 100자 이하여야 합니다.")
        String name,
    @Size(max = 2000, message = "X 프로필 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "올바른 X 프로필 URL을 입력해주세요.")
        String xProfileUrl,
    @Size(max = 2000, message = "Threads 프로필 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "올바른 Threads 프로필 URL을 입력해주세요.")
        String threadsProfileUrl,
    @Size(max = 2000, message = "YouTube 채널 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "올바른 YouTube 채널 URL을 입력해주세요.")
        String youtubeProfileUrl,
    @Valid FeaturedPersonImageSelectionRequest profileImageSelection) {}
