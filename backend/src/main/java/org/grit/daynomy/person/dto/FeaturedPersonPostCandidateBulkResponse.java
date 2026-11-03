package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FeaturedPersonPostCandidateBulkResponse(
    @Schema(description = "성공한 후보 목록") List<Success> succeeded,
    @Schema(description = "실패한 후보 목록") List<Failure> failed) {

  public record Success(
      @Schema(description = "게시물 후보 ID", example = "1") Long candidateId,
      @Schema(description = "승인으로 생성된 게시물. 거절 성공이면 null", nullable = true)
          FeaturedPersonPostResponse post) {}

  public record Failure(
      @Schema(description = "게시물 후보 ID", example = "2") Long candidateId,
      @Schema(description = "실패 코드", example = "FEATURED_PERSON_POST_CANDIDATE_NOT_FOUND")
          String errorCode,
      @Schema(description = "실패 사유", example = "수집한 게시물 후보를 찾을 수 없습니다.") String message) {}
}
