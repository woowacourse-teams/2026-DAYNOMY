package org.grit.daynomy.person.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.grit.daynomy.content.domain.ContentSourceType;

public record FeaturedPersonPostRequest(
    @NotNull(message = "출처를 선택해주세요.") ContentSourceType sourceType,
    @Size(max = 255, message = "제목은 255자 이하여야 합니다.") String title,
    @NotBlank(message = "게시물 URL을 입력해주세요.")
        @Size(max = 2000, message = "게시물 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "올바른 게시물 URL을 입력해주세요.")
        String url) {}
