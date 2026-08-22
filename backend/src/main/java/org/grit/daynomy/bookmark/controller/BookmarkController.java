package org.grit.daynomy.bookmark.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.bookmark.dto.BookmarkRequest;
import org.grit.daynomy.bookmark.dto.BookmarkResponse;
import org.grit.daynomy.bookmark.service.BookmarkService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Bookmark", description = "관심 자산 북마크 API")
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
    BookmarkResponse response =
        BookmarkResponse.from(
            bookmarkService.addBookmark(authenticatedMember.memberId(), request.targetId()));
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }
}
