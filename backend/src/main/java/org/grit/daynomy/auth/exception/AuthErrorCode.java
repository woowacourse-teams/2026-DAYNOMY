package org.grit.daynomy.auth.exception;

import org.grit.daynomy.common.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AuthErrorCode implements ErrorCode {
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요합니다."),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 인증 정보입니다."),
  REFRESH_TOKEN_REQUIRED(HttpStatus.UNAUTHORIZED, "Refresh Token이 필요합니다."),
  INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다."),
  FORBIDDEN(HttpStatus.FORBIDDEN, "접근 권한이 없습니다.");

  private final HttpStatus status;
  private final String message;

  AuthErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

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
