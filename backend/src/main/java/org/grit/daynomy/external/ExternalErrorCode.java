package org.grit.daynomy.external;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ExternalErrorCode implements ErrorCode {

  PUBLIC_DATA_API_REQUEST_FAILED(HttpStatus.BAD_GATEWAY, "공공데이터 API 요청에 실패했습니다."),
  S3_IMAGE_STORAGE_FAILED(HttpStatus.BAD_GATEWAY, "이미지 저장소 요청에 실패했습니다."),
  AI_NEWS_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 뉴스 생성에 실패했습니다."),
  AI_IMAGE_GENERATION_FAILED(HttpStatus.BAD_GATEWAY, "AI 이미지 생성에 실패했습니다."),
  AI_PORTFOLIO_ANALYSIS_FAILED(HttpStatus.BAD_GATEWAY, "AI 포트폴리오 분석 생성에 실패했습니다.");

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
