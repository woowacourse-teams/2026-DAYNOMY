package org.grit.daynomy.pmfsurvey.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.pmfsurvey.dto.PmfSurveyResultsResponse;
import org.grit.daynomy.pmfsurvey.service.PmfSurveyService;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/pmf-surveys")
public class AdminPmfSurveyController {

  private final PmfSurveyService service;

  @Operation(summary = "PMF 설문 그룹별 응답 및 선택지 집계 조회")
  @GetMapping("/{surveyId}/results")
  public ResponseEntity<PmfSurveyResultsResponse> results(@PathVariable @Positive long surveyId) {
    return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.results(surveyId));
  }
}
