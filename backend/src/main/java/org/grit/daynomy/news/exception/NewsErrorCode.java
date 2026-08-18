package org.grit.daynomy.news.exception;

import org.grit.daynomy.common.ErrorCode;
import org.springframework.http.HttpStatus;

public enum NewsErrorCode implements ErrorCode {
  NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스를 찾을 수 없습니다.");

  private final HttpStatus status;
  private final String message;

  NewsErrorCode(HttpStatus status, String message) {
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
