package org.grit.daynomy.bookmark.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.bookmark.domain.Bookmark;

public record BookmarkResponse(
    @Schema(description = "북마크 ID", example = "1") Long id,
    @Schema(description = "자산 ID", example = "1") Long targetId,
    @Schema(description = "자산 이름", example = "에코프로비엠") String assetName) {

  public static BookmarkResponse from(Bookmark bookmark) {
    return new BookmarkResponse(
        bookmark.getId(), bookmark.getAsset().getId(), bookmark.getAsset().getName());
  }
}
