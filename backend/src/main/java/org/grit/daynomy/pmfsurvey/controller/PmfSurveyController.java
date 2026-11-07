package org.grit.daynomy.pmfsurvey.controller;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.pmfsurvey.dto.PmfSubmissionRequest;
import org.grit.daynomy.pmfsurvey.dto.PmfSurveyResponse;
import org.grit.daynomy.pmfsurvey.exception.PmfSurveyErrorCode;
import org.grit.daynomy.pmfsurvey.service.PmfSurveyService;
import org.grit.daynomy.pmfsurvey.service.PmfSurveyService.Submission;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/pmf-surveys")
public class PmfSurveyController {

  private final PmfSurveyService service;
  private final PmfSurveyIdentity identity;

  @Operation(summary = "현재 PMF 설문 노출 상태 조회")
  @GetMapping("/current")
  public ResponseEntity<PmfSurveyResponse> current(
      @AuthenticationPrincipal AuthenticatedMember member,
      HttpServletRequest request,
      HttpServletResponse response) {
    identity.requireValidToken(request);
    long surveyId = service.activeSurveyId();
    if (surveyId == 0) {
      return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
    if (identity.excluded(member, request)) {
      return ResponseEntity.ok()
          .cacheControl(CacheControl.noStore())
          .body(new PmfSurveyResponse(surveyId, false, null, true, null));
    }
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(
            service.current(
                identity.getOrCreate(member, request, response),
                identity.visitSession(request),
                member == null ? null : member.memberId()));
  }

  @Operation(summary = "필수 응답과 선택 이유를 한 번에 저장")
  @PostMapping("/{surveyId}/responses")
  public ResponseEntity<SubmissionResponse> submit(
      @PathVariable @Positive long surveyId,
      @Valid @RequestBody PmfSubmissionRequest submission,
      @AuthenticationPrincipal AuthenticatedMember member,
      HttpServletRequest request) {
    Submission result = service.submit(surveyId, writeRespondent(member, request), submission);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status)
        .cacheControl(CacheControl.noStore())
        .body(new SubmissionResponse(result.id()));
  }

  @Operation(summary = "다음 한국 시간 자정까지 PMF 설문 숨김")
  @PostMapping("/{surveyId}/snooze")
  public ResponseEntity<Void> snooze(
      @PathVariable @Positive long surveyId,
      @AuthenticationPrincipal AuthenticatedMember member,
      HttpServletRequest request) {
    service.snooze(surveyId, writeRespondent(member, request));
    return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
  }

  private String writeRespondent(AuthenticatedMember member, HttpServletRequest request) {
    identity.requireValidToken(request);
    if (identity.excluded(member, request)) {
      throw new BusinessException(PmfSurveyErrorCode.PMF_SURVEY_INELIGIBLE);
    }
    return identity.requireExisting(member, request);
  }

  public record SubmissionResponse(long id) {}
}
