package org.grit.daynomy.search.exception;

import org.grit.daynomy.common.ErrorCode;
import org.springframework.http.HttpStatus;

public enum SearchErrorCode implements ErrorCode {
  SEARCH_KEYWORD_REQUIRED(HttpStatus.BAD_REQUEST, "검색어를 입력해주세요."),
  SEARCH_INVALID_KEYWORD(HttpStatus.BAD_REQUEST, "올바른 검색어를 입력해주세요."),
  SEARCH_INVALID_CATEGORY(HttpStatus.BAD_REQUEST, "존재하지 않는 카테고리입니다."),
  SEARCH_INVALID_PAGE_CONDITION(HttpStatus.BAD_REQUEST, "검색 페이지 조건이 올바르지 않습니다.");

  private final HttpStatus status;
  private final String message;

  SearchErrorCode(HttpStatus status, String message) {
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
