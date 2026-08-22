package org.grit.daynomy.bookmark.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.bookmark.dto.BookmarkRequest;
import org.grit.daynomy.bookmark.dto.BookmarkResponse;
import org.grit.daynomy.bookmark.service.BookmarkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookmark", description = "관심 자산 북마크 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api")
@RestController
public class BookmarkController {

  private final BookmarkService bookmarkService;

  @Operation(summary = "관심 자산 추가", description = "로그인한 회원의 관심 자산을 북마크합니다.")
  @PostMapping("/assets/bookmarks")
  public ResponseEntity<BookmarkResponse> addBookmark(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
      @Valid @RequestBody BookmarkRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(bookmarkService.addBookmark(authenticatedMember.memberId(), request.targetId()));
  }

  @Operation(summary = "관심 자산 삭제", description = "로그인한 회원의 관심 자산 북마크를 삭제합니다.")
  @DeleteMapping("/assets/bookmarks")
  public ResponseEntity<Void> deleteBookmark(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember,
      @Parameter(description = "삭제할 자산 ID", example = "1")
          @RequestParam
          @Positive(message = "자산 ID는 1 이상이어야 합니다.")
          Long targetId) {
    bookmarkService.deleteBookmark(authenticatedMember.memberId(), targetId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "내 관심 자산 조회", description = "로그인한 회원이 북마크한 자산 목록을 조회합니다.")
  @GetMapping("/users/me/bookmarks")
  public ResponseEntity<List<BookmarkResponse>> getMyBookmarks(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember authenticatedMember) {
    return ResponseEntity.ok(bookmarkService.getBookmarks(authenticatedMember.memberId()));
  }
}
