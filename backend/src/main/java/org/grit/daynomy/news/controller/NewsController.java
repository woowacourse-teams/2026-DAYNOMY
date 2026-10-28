package org.grit.daynomy.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.dto.NewsDetailResponse;
import org.grit.daynomy.news.dto.NewsPageResponse;
import org.grit.daynomy.news.service.NewsService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "News", description = "뉴스 조회 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/news")
@RestController
public class NewsController {

  private final NewsService newsService;

  @Operation(summary = "뉴스 목록 조회", description = "뉴스 목록을 페이지 단위로 조회합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = NewsPageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "카테고리 또는 페이지 조건 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<NewsPageResponse> getNewsPage(
      @Parameter(description = "1부터 시작하는 페이지 번호", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
          int page,
      @Parameter(description = "페이지 크기", example = "15")
          @RequestParam(defaultValue = "15")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size,
      @Parameter(description = "뉴스 카테고리. 생략하면 전체 조회", example = "STOCK")
          @RequestParam(required = false)
          Category category) {
    return ResponseEntity.ok(newsService.getNewsPage(page, size, category));
  }

  @Operation(summary = "오늘의 뉴스 조회", description = "오늘 발행된 뉴스 중 최신 뉴스 9건을 조회합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "조회 성공",
      content = @Content(schema = @Schema(implementation = NewsPageResponse.class)))
  @GetMapping("/today")
  public ResponseEntity<NewsPageResponse> getTodayNews() {
    return ResponseEntity.ok(newsService.getTodayNews());
  }

  @Operation(summary = "뉴스 상세 조회", description = "뉴스 ID로 단일 뉴스를 조회합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = NewsDetailResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "뉴스를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/{id}")
  public ResponseEntity<NewsDetailResponse> getNewsDetail(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long id) {
    return ResponseEntity.ok(newsService.getNewsDetail(id));
  }
}
