package org.grit.daynomy.person.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
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
  @GetMapping
  public ResponseEntity<FeaturedPeopleResponse> getPeople() {
    return ResponseEntity.ok(featuredPersonService.getPeople());
  }

  @Operation(summary = "주요 인물 등록")
  @PostMapping
  public ResponseEntity<FeaturedPersonResponse> createPerson(
      @Valid @RequestBody FeaturedPersonRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(featuredPersonService.createPerson(request));
  }

  @Operation(summary = "주요 인물 수정")
  @PutMapping("/{personId}")
  public ResponseEntity<FeaturedPersonResponse> updatePerson(
      @PathVariable @Positive Long personId,
      @Valid @RequestBody FeaturedPersonRequest request) {
    return ResponseEntity.ok(featuredPersonService.updatePerson(personId, request));
  }

  @Operation(summary = "주요 인물 Threads·X 게시물 AI 검색")
  @GetMapping("/{personId}/posts/search")
  public ResponseEntity<SocialPostSearchResponse> searchPosts(
      @PathVariable @Positive Long personId) {
    return ResponseEntity.ok(featuredPersonService.searchPosts(personId));
  }

  @Operation(summary = "주요 인물 삭제")
  @DeleteMapping("/{personId}")
  public ResponseEntity<Void> deletePerson(@PathVariable @Positive Long personId) {
    featuredPersonService.deletePerson(personId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "주요 인물 게시물 등록")
  @PostMapping("/{personId}/posts")
  public ResponseEntity<FeaturedPersonPostResponse> createPost(
      @PathVariable @Positive Long personId,
      @Valid @RequestBody FeaturedPersonPostRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(featuredPersonService.createPost(personId, request));
  }

  @Operation(summary = "주요 인물 게시물 삭제")
  @DeleteMapping("/{personId}/posts/{postId}")
  public ResponseEntity<Void> deletePost(
      @PathVariable @Positive Long personId, @PathVariable @Positive Long postId) {
    featuredPersonService.deletePost(personId, postId);
    return ResponseEntity.noContent().build();
  }
}
