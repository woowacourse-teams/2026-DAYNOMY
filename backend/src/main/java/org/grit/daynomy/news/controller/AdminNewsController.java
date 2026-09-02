package org.grit.daynomy.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsResponse;
import org.grit.daynomy.news.service.AdminNewsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin News", description = "관리자 뉴스 관리 API")
@RequiredArgsConstructor
@RequestMapping("/api/admin/news")
@RestController
public class AdminNewsController {

  private final AdminNewsService adminNewsService;

  @Operation(summary = "뉴스 등록", description = "관리자용 뉴스를 초안 상태로 등록합니다.")
  @PostMapping
  public ResponseEntity<AdminNewsResponse> createNews(
      @Valid @RequestBody AdminNewsCreateRequest request) {
    News news = adminNewsService.createDraft(request);

    return ResponseEntity.status(HttpStatus.CREATED).body(AdminNewsResponse.from(news));
  }
}
