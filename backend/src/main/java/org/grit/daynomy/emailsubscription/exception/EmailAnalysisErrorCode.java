package org.grit.daynomy.emailsubscription.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum EmailAnalysisErrorCode implements ErrorCode {
  EMAIL_ANALYSIS_NOT_FOUND;

  @Override
  public String code() {
    return name();
  }

  @Override
  public HttpStatus status() {
    return HttpStatus.NOT_FOUND;
  }

  @Override
  public String message() {
    return "공통 분석을 찾을 수 없습니다.";
  }
}
