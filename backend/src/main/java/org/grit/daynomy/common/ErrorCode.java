package org.grit.daynomy.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스를 찾을 수 없습니다."),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다."),
  SEARCH_KEYWORD_REQUIRED(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요."),
  INVALID_SEARCH_KEYWORD(HttpStatus.BAD_REQUEST, "올바른 검색어를 입력해주세요."),
  INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다.");

  private final HttpStatus status;
  private final String message;

  ErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public HttpStatus status() {
    return status;
  }

  public String message() {
    return message;
  }
}
