package org.grit.daynomy.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.content.dto.ContentCollectionBatchResultResponse;
import org.grit.daynomy.content.dto.ContentCollectionManualRequest;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.dto.ContentCollectionSourceRequest;
import org.grit.daynomy.content.dto.ContentCollectionSourceResponse;
import org.grit.daynomy.content.dto.ContentCollectionSourcesResponse;
import org.grit.daynomy.content.service.ContentCollectionBatchService;
import org.grit.daynomy.content.service.ContentCollectionManualService;
import org.grit.daynomy.content.service.ContentCollectionService;
import org.grit.daynomy.content.service.ContentCollectionSourceService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Content Collection", description = "관리자용 외부 콘텐츠 자동 수집 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/content-collection-sources")
@RestController
public class AdminContentCollectionController {

  private final ContentCollectionSourceService sourceService;
  private final ContentCollectionService collectionService;
  private final ContentCollectionManualService manualService;
  private final ContentCollectionBatchService batchService;

  @Operation(summary = "콘텐츠 수집 출처 목록 조회")
  @GetMapping
  public ResponseEntity<ContentCollectionSourcesResponse> getSources() {
    return ResponseEntity.ok(sourceService.getSources());
  }

  @Operation(summary = "콘텐츠 수집 출처 등록")
  @PostMapping
  public ResponseEntity<ContentCollectionSourceResponse> createSource(
      @Valid @RequestBody ContentCollectionSourceRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(sourceService.create(request));
  }

  @Operation(summary = "활성 콘텐츠 수집 출처 전체 즉시 수집")
  @PostMapping("/collect-all")
  public ResponseEntity<ContentCollectionBatchResultResponse> collectAll() {
    return ResponseEntity.ok(batchService.collectAllEnabled());
  }

  @Operation(summary = "콘텐츠 수집 출처 수정")
  @PutMapping("/{sourceId}")
  public ResponseEntity<ContentCollectionSourceResponse> updateSource(
      @PathVariable @Positive Long sourceId,
      @Valid @RequestBody ContentCollectionSourceRequest request) {
    return ResponseEntity.ok(sourceService.update(sourceId, request));
  }

  @Operation(summary = "콘텐츠 수집 출처 즉시 수집")
  @PostMapping("/{sourceId}/collect")
  public ResponseEntity<ContentCollectionResultResponse> collect(
      @PathVariable @Positive Long sourceId) {
    return ResponseEntity.ok(collectionService.collect(sourceId));
  }

  @Operation(summary = "수집 출처의 게시물 URL 수동 등록")
  @PostMapping("/{sourceId}/contents")
  public ResponseEntity<ContentCollectionResultResponse> registerContent(
      @PathVariable @Positive Long sourceId,
      @Valid @RequestBody ContentCollectionManualRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(manualService.register(sourceId, request));
  }
}
