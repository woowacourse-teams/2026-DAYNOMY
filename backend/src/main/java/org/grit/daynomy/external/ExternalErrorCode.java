package org.grit.daynomy.external;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ExternalErrorCode implements ErrorCode {
  DART_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "DART API 요청에 실패했습니다."),
  DART_NEWS_MAPPING_FAILED(HttpStatus.BAD_GATEWAY, "DART 뉴스 변환에 실패했습니다."),
  KOSIS_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "KOSIS API 요청에 실패했습니다."),
  BOK_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "한국은행 API 요청에 실패했습니다."),
  PUBLIC_DATA_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "공공데이터 API 요청에 실패했습니다."),
  AI_NEWS_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 뉴스 생성에 실패했습니다."),
  AI_IMAGE_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 이미지 생성에 실패했습니다.");

  private final HttpStatus status;
  private final String message;

  ExternalErrorCode(HttpStatus status, String message) {
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
