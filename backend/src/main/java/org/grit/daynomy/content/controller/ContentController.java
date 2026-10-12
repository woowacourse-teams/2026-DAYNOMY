package org.grit.daynomy.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.content.dto.ContentPageResponse;
import org.grit.daynomy.content.service.AssetContentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RequiredArgsConstructor
@RequestMapping("/api/contents")
@RestController
public class ContentController {
  private final AssetContentService service;

  @Operation(
      summary = "소식의 공개 외부 자료 목록",
      description = "저장된 YouTube·Threads·X 자료를 등록일, ID 내림차순으로 조회합니다. 페이지는 1부터 시작합니다.")
  @GetMapping
  public ContentPageResponse getContents(
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size) {
    return service.getPublicContents(page, size);
  }
}
