package org.grit.daynomy.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.ApiResponse;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.dto.NewsDetailResponse;
import org.grit.daynomy.news.dto.NewsPageResponse;
import org.grit.daynomy.news.service.NewsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "News", description = "뉴스 조회 API")
@RequiredArgsConstructor
@RequestMapping("/api/news")
@RestController
public class NewsController {

  private final NewsService newsService;

  @Operation(summary = "뉴스 목록 조회", description = "뉴스 목록을 페이지 단위로 조회합니다.")
  @GetMapping
  public ApiResponse<NewsPageResponse> getNewsPage(
      @Parameter(description = "1부터 시작하는 페이지 번호", example = "1") @RequestParam(defaultValue = "1")
          int page,
      @Parameter(description = "페이지 크기", example = "15") @RequestParam(defaultValue = "15")
          int size,
      @Parameter(description = "뉴스 카테고리") @RequestParam(required = false) Category category) {
    return ApiResponse.success("뉴스를 조회했습니다.", newsService.getNewsPage(page, size, category));
  }

  @Operation(summary = "뉴스 상세 조회", description = "뉴스 ID로 단일 뉴스를 조회합니다.")
  @GetMapping("/{id}")
  public ApiResponse<NewsDetailResponse> getNewsDetail(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long id) {
    return ApiResponse.success("뉴스를 조회했습니다.", newsService.getNewsDetail(id));
  }
}
