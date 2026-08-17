package org.grit.daynomy.common;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  NEWS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스를 찾을 수 없습니다."),
  DART_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "DART API 요청에 실패했습니다."),
  DART_NEWS_MAPPING_FAILED(HttpStatus.BAD_GATEWAY, "DART 뉴스 변환에 실패했습니다."),
  AI_NEWS_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 뉴스 생성에 실패했습니다."),
  INVALID_REQUEST(HttpStatus.BAD_REQUEST, "잘못된 요청입니다.");

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
