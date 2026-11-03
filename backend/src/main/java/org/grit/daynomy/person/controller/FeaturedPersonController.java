package org.grit.daynomy.person.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.person.dto.FeaturedPeoplePageResponse;
import org.grit.daynomy.person.service.FeaturedPersonService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주요 인물", description = "주요 인물과 공개 소셜 게시물 조회 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/people")
@RestController
public class FeaturedPersonController {

  private final FeaturedPersonService featuredPersonService;

  @Operation(summary = "주요 인물과 공개 게시물 페이지 조회")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content = @Content(schema = @Schema(implementation = FeaturedPeoplePageResponse.class)))
  @GetMapping
  public ResponseEntity<FeaturedPeoplePageResponse> getPeople(
      @Parameter(description = "1부터 시작하는 페이지 번호", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
          int page,
      @Parameter(description = "페이지 크기", example = "6")
          @RequestParam(defaultValue = "6")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size,
      @Parameter(description = "주요 인물 ID. 생략하면 전체 게시물을 조회", example = "1")
          @RequestParam(required = false)
          @Positive(message = "주요 인물 ID는 양수여야 합니다.")
          Long personId) {
    return ResponseEntity.ok(featuredPersonService.getPeople(page, size, personId));
  }
}
