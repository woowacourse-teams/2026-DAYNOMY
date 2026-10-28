package org.grit.daynomy.content.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.grit.daynomy.content.domain.ContentSourceType;

public record ContentCollectionSourceRequest(
    @Schema(description = "수집 출처 이름", example = "한국경제TV")
        @NotBlank(message = "수집 출처 이름을 입력해주세요.")
        @Size(max = 100, message = "수집 출처 이름은 100자 이하여야 합니다.")
        String name,
    @Schema(description = "수집 플랫폼", example = "YOUTUBE") @NotNull ContentSourceType sourceType,
    @Schema(description = "채널 또는 프로필 URL", example = "https://www.youtube.com/@hkwowtv")
        @NotBlank(message = "수집 출처 URL을 입력해주세요.")
        @Size(max = 2000, message = "수집 출처 URL은 2000자 이하여야 합니다.")
        @Pattern(regexp = "https?://\\S+", message = "올바른 수집 출처 URL을 입력해주세요.")
        String sourceUrl,
    @Schema(description = "공식 계정에 직접 연결할 종목 ID. 경제 채널은 생략", example = "1") @Positive Long assetId,
    @Schema(description = "예약 수집 활성화 여부", example = "true") boolean enabled) {}
