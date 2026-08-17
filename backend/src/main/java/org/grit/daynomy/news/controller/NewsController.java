package org.grit.daynomy.news.controller;

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

@RequiredArgsConstructor
@RestController
@RequestMapping("/api/news")
public class NewsController {

  private final NewsService newsService;

  @GetMapping
  public ApiResponse<NewsPageResponse> getNewsPage(
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "15") int size,
      @RequestParam(required = false) Category category) {
    return ApiResponse.success("뉴스를 조회했습니다.", newsService.getNewsPage(page, size, category));
  }

  @GetMapping("/{id}")
  public ApiResponse<NewsDetailResponse> getNewsDetail(@PathVariable Long id) {
    return ApiResponse.success("뉴스를 조회했습니다.", newsService.getNewsDetail(id));
  }
}
