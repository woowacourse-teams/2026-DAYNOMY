package org.grit.daynomy.news.controller;

import org.grit.daynomy.common.api.ApiResponse;
import org.grit.daynomy.news.dto.NewsSearchResponse;
import org.grit.daynomy.news.service.NewsSearchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search/news")
public class NewsSearchController {

  private final NewsSearchService newsSearchService;

  public NewsSearchController(NewsSearchService newsSearchService) {
    this.newsSearchService = newsSearchService;
  }

  @GetMapping
  public ApiResponse<NewsSearchResponse> search(
      @RequestParam(name = "q", required = false) String keyword,
      @RequestParam(required = false) String category,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    NewsSearchResponse result = newsSearchService.search(keyword, category, page, size);

    if (result.content().isEmpty()) {
      return ApiResponse.success("검색 결과가 없습니다.", result);
    }
    if (result.category() != null) {
      return ApiResponse.success("카테고리 필터 검색 결과를 조회했습니다.", result);
    }
    return ApiResponse.success("뉴스 검색 결과를 조회했습니다.", result);
  }
}
