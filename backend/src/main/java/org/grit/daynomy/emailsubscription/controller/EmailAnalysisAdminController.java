package org.grit.daynomy.emailsubscription.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.dto.AdminEmailAnalysisDetailResponse;
import org.grit.daynomy.emailsubscription.dto.AdminEmailAnalysisPageResponse;
import org.grit.daynomy.emailsubscription.dto.AnalysisInvalidationRequest;
import org.grit.daynomy.emailsubscription.dto.AnalysisInvalidationResponse;
import org.grit.daynomy.emailsubscription.service.CommonStockAnalysisService;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin Email Analysis", description = "공통 분석 오류·정정 처리 API")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/email-analyses")
public class EmailAnalysisAdminController {
  private final CommonStockAnalysisService service;

  @Operation(
      summary = "관리자 공통 분석 목록 조회",
      description = "분석 시각·ID 내림차순으로 조회합니다. page는 0부터 시작하며, status와 assetId 필터를 선택할 수 있습니다.")
  @GetMapping
  public AdminEmailAnalysisPageResponse list(
      @RequestParam(required = false) CommonStockAnalysis.Status status,
      @RequestParam(required = false) @Positive Long assetId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
    return service.listForAdmin(status, assetId, PageRequest.of(page, size));
  }

  @Operation(
      summary = "관리자 공통 분석 상세 조회",
      description = "분석 내용·원문 출처·검증 결과·무효화 정보를 반환합니다. 검증 대기·거절·무효화된 결과도 조회할 수 있습니다.")
  @GetMapping("/{id}")
  public AdminEmailAnalysisDetailResponse detail(@PathVariable @Positive Long id) {
    return service.detailForAdmin(id);
  }

  @Operation(
      summary = "오류 분석 무효화 및 대기 메일 취소",
      description = "분석 원문과 최초 무효화 사유·시각을 보존합니다. 이미 전송 중이거나 접수된 메일은 회수하지 않습니다.")
  @PostMapping("/{id}/invalidate")
  public AnalysisInvalidationResponse invalidate(
      @PathVariable Long id, @Valid @RequestBody AnalysisInvalidationRequest request) {
    return service.invalidate(id, request.reason());
  }

  @Operation(
      summary = "정정 검토 대상 발송 ID 조회",
      description =
          "해당 분석을 포함한 접수 완료·전송 중·결과 불명확 메일을 조회합니다. 발송 상태가 달라질 수 있으므로 전송 중 작업은 이후 다시 확인해야 합니다.")
  @GetMapping("/{id}/correction-review-deliveries")
  public List<UUID> correctionReviewIds(
      @PathVariable Long id,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "50") @Min(1) @Max(100) int size) {
    return service.correctionReviewIds(id, PageRequest.of(page, size));
  }
}
