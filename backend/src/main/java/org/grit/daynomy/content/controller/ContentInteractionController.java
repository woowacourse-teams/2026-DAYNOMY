package org.grit.daynomy.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.content.dto.ContentInteractionRequest;
import org.grit.daynomy.content.dto.ContentPopularityResponse;
import org.grit.daynomy.content.service.ContentInteractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "콘텐츠 인기", description = "콘텐츠 반응 기록 및 인기 점수 조회 API")
@Validated
@RequiredArgsConstructor
@RestController
public class ContentInteractionController {

  private final ContentInteractionService contentInteractionService;

  @Operation(summary = "콘텐츠 반응 기록")
  @PostMapping("/api/content-interactions")
  public ResponseEntity<Void> record(@Valid @RequestBody ContentInteractionRequest request) {
    contentInteractionService.record(request);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "콘텐츠 인기 점수 조회", description = "지정한 최근 기간의 콘텐츠별 인기 점수를 조회합니다.")
  @GetMapping("/api/content-rankings")
  public ResponseEntity<ContentPopularityResponse> getPopularity(
      @RequestParam(defaultValue = "7") @Min(1) @Max(30) int periodDays) {
    return ResponseEntity.ok(contentInteractionService.getPopularity(periodDays));
  }
}
