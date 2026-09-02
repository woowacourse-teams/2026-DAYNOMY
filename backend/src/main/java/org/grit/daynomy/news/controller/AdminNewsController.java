package org.grit.daynomy.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsPageResponse;
import org.grit.daynomy.news.dto.AdminNewsResponse;
import org.grit.daynomy.news.service.AdminNewsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin News", description = "관리자 뉴스 관리 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/news")
@RestController
public class AdminNewsController {

  private final AdminNewsService adminNewsService;

  @Operation(summary = "관리자 뉴스 목록 조회", description = "관리자용 뉴스 목록을 상태·카테고리별로 조회합니다.")
  @GetMapping
  public ResponseEntity<AdminNewsPageResponse> getNewsPage(
      @Parameter(description = "1부터 시작하는 페이지 번호", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
          int page,
      @Parameter(description = "페이지 크기", example = "15")
          @RequestParam(defaultValue = "15")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size,
      @Parameter(description = "뉴스 상태") @RequestParam(required = false) NewsStatus status,
      @Parameter(description = "뉴스 카테고리") @RequestParam(required = false) Category category) {
    return ResponseEntity.ok(adminNewsService.getNewsPage(page, size, status, category));
  }

  @Operation(summary = "뉴스 등록", description = "관리자용 뉴스를 초안 상태로 등록합니다.")
  @PostMapping
  public ResponseEntity<AdminNewsResponse> createNews(
      @Valid @RequestBody AdminNewsCreateRequest request) {
    News news = adminNewsService.createDraft(request);

    return ResponseEntity.status(HttpStatus.CREATED).body(AdminNewsResponse.from(news));
  }
}
