package org.grit.daynomy.content.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ContentErrorCode implements ErrorCode {
  ASSET_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "종목 관련 자료를 찾을 수 없습니다."),
  ASSET_CONTENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 URL입니다."),
  CONTENT_NOT_AVAILABLE(HttpStatus.NOT_FOUND, "공개된 콘텐츠를 찾을 수 없습니다."),
  CONTENT_INTERACTION_RATE_LIMITED(
      HttpStatus.TOO_MANY_REQUESTS, "콘텐츠 반응 요청이 많습니다. 잠시 후 다시 시도해 주세요.");

  private final HttpStatus status;
  private final String message;

  ContentErrorCode(HttpStatus status, String message) {
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
