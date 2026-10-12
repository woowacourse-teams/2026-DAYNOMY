package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.external.wikimedia.WikimediaImageCandidate;

public record WikimediaImageCandidateResponse(
    @Schema(description = "Wikimedia Commons 파일 제목") String title,
    @Schema(description = "미리보기 이미지 URL") String thumbnailUrl,
    @Schema(description = "원본 파일 페이지 URL") String sourceUrl,
    @Schema(description = "저작자") String author,
    @Schema(description = "라이선스") String license,
    @Schema(description = "라이선스 URL") String licenseUrl,
    @Schema(description = "이미지 너비") int width,
    @Schema(description = "이미지 높이") int height) {

  public static WikimediaImageCandidateResponse from(WikimediaImageCandidate candidate) {
    return new WikimediaImageCandidateResponse(
        candidate.title(),
        candidate.thumbnailUrl(),
        candidate.sourceUrl(),
        candidate.author(),
        candidate.license(),
        candidate.licenseUrl(),
        candidate.width(),
        candidate.height());
  }
}
