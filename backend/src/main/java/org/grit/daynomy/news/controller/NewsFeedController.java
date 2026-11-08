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
import org.grit.daynomy.news.domain.NewsFeedSort;
import org.grit.daynomy.news.domain.NewsFeedSource;
import org.grit.daynomy.news.dto.NewsFeedResponse;
import org.grit.daynomy.news.service.NewsFeedService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "News", description = "뉴스 조회 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/news/feed")
@RestController
public class NewsFeedController {

  private final NewsFeedService service;

  @Operation(
      summary = "소식 탭 피드 조회",
      description =
          "발행 기사, 인물의 글, YouTube·Threads·X 자료를 출처별로 필터링하고 최신순 또는 최근 7일 인기순으로 조회합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = NewsFeedResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "출처, 정렬 또는 페이지 조건 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public NewsFeedResponse getFeed(
      @RequestParam(defaultValue = "ALL") NewsFeedSource source,
      @Parameter(description = "페이지 번호(1부터 시작)")
          @RequestParam(defaultValue = "1")
          @Min(1)
          int page,
      @Parameter(description = "페이지 크기(1~100)")
          @RequestParam(defaultValue = "8")
          @Min(1)
          @Max(100)
          int size,
      @RequestParam(defaultValue = "LATEST") NewsFeedSort sort) {
    return service.getFeed(source, page, size, sort);
  }
}
