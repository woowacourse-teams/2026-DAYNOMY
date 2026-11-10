package org.grit.daynomy.pmfsurvey.exception;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

@RequiredArgsConstructor
public enum PmfSurveyErrorCode implements ErrorCode {
  PMF_SURVEY_NOT_FOUND(HttpStatus.NOT_FOUND, "진행 중인 설문을 찾을 수 없습니다."),
  PMF_SURVEY_INELIGIBLE(HttpStatus.FORBIDDEN, "이 설문에 참여할 수 없습니다."),
  PMF_SURVEY_VISITOR_REQUIRED(HttpStatus.CONFLICT, "설문 상태를 먼저 조회해 주세요.");

  private final HttpStatus status;
  private final String message;

  @Override
  public String code() {
    return name();
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String message() {
    return message;
  }
}
