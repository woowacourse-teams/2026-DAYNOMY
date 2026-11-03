package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashSet;
import java.util.List;

public record FeaturedPersonPostCandidateBulkRequest(
    @Schema(description = "처리할 게시물 후보 ID 목록", example = "[1, 2, 3]")
        @NotEmpty(message = "처리할 게시물 후보를 선택해주세요.")
        @Size(max = 100, message = "게시물 후보는 한 번에 최대 100개까지 처리할 수 있습니다.")
        List<@NotNull @Positive Long> candidateIds) {

  public FeaturedPersonPostCandidateBulkRequest {
    candidateIds = candidateIds == null ? null : List.copyOf(new LinkedHashSet<>(candidateIds));
  }
}
