package org.grit.daynomy.person.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.content.dto.SocialPostSearchResponse;
import org.grit.daynomy.person.dto.FeaturedPeopleResponse;
import org.grit.daynomy.person.dto.FeaturedPersonPostRequest;
import org.grit.daynomy.person.dto.FeaturedPersonPostResponse;
import org.grit.daynomy.person.dto.FeaturedPersonRequest;
import org.grit.daynomy.person.dto.FeaturedPersonResponse;
import org.grit.daynomy.person.service.FeaturedPersonService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Featured People", description = "관리자용 주요 인물 관리 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/people")
@RestController
public class AdminFeaturedPersonController {

  private final FeaturedPersonService featuredPersonService;

  @Operation(summary = "주요 인물 목록 조회")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content = @Content(schema = @Schema(implementation = FeaturedPeopleResponse.class)))
  @GetMapping
  public ResponseEntity<FeaturedPeopleResponse> getPeople() {
    return ResponseEntity.ok(featuredPersonService.getPeople());
  }

  @Operation(summary = "주요 인물 등록")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "등록 성공",
        content = @Content(schema = @Schema(implementation = FeaturedPersonResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 또는 소셜 프로필 URL 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "이미 등록된 인물",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<FeaturedPersonResponse> createPerson(
      @Valid @RequestBody FeaturedPersonRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(featuredPersonService.createPerson(request));
  }

  @Operation(summary = "주요 인물 수정")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "수정 성공",
        content = @Content(schema = @Schema(implementation = FeaturedPersonResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "인물 ID, 요청 또는 소셜 프로필 URL 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "주요 인물을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "이미 등록된 인물",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PutMapping("/{personId}")
  public ResponseEntity<FeaturedPersonResponse> updatePerson(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId,
      @Valid @RequestBody FeaturedPersonRequest request) {
    return ResponseEntity.ok(featuredPersonService.updatePerson(personId, request));
  }

  @Operation(summary = "주요 인물 Threads·X 게시물 AI 검색")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "검색 성공",
        content = @Content(schema = @Schema(implementation = SocialPostSearchResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "인물 ID 또는 소셜 프로필 URL 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "주요 인물을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/{personId}/posts/search")
  public ResponseEntity<SocialPostSearchResponse> searchPosts(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId) {
    return ResponseEntity.ok(featuredPersonService.searchPosts(personId));
  }

  @Operation(summary = "주요 인물 삭제")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "인물 ID 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "주요 인물을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/{personId}")
  public ResponseEntity<Void> deletePerson(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId) {
    featuredPersonService.deletePerson(personId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "주요 인물 게시물 등록")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "등록 성공",
        content = @Content(schema = @Schema(implementation = FeaturedPersonPostResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "인물 ID, 요청 또는 게시물 URL 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "주요 인물을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "이미 등록된 게시물 URL",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/{personId}/posts")
  public ResponseEntity<FeaturedPersonPostResponse> createPost(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId,
      @Valid @RequestBody FeaturedPersonPostRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(featuredPersonService.createPost(personId, request));
  }

  @Operation(summary = "주요 인물 게시물 삭제")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "400",
        description = "인물 또는 게시물 ID 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "주요 인물 또는 게시물을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/{personId}/posts/{postId}")
  public ResponseEntity<Void> deletePost(
      @Parameter(description = "주요 인물 ID", example = "1") @PathVariable @Positive Long personId,
      @Parameter(description = "게시물 ID", example = "10") @PathVariable @Positive Long postId) {
    featuredPersonService.deletePost(personId, postId);
    return ResponseEntity.noContent().build();
  }
}
