package org.grit.daynomy.content.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ContentErrorCode implements ErrorCode {
  ASSET_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "종목 관련 자료를 찾을 수 없습니다."),
  ASSET_CONTENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 URL입니다.");

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
