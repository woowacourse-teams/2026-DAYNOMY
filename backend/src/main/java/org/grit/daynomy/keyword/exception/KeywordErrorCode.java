package org.grit.daynomy.keyword.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum KeywordErrorCode implements ErrorCode {
  KEYWORD_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스의 키워드를 찾을 수 없습니다.");

  private final HttpStatus status;
  private final String message;

  KeywordErrorCode(HttpStatus status, String message) {
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
