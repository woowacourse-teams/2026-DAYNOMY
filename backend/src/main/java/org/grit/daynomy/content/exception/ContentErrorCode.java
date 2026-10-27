package org.grit.daynomy.content.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ContentErrorCode implements ErrorCode {
  ASSET_CONTENT_NOT_FOUND(HttpStatus.NOT_FOUND, "종목 관련 자료를 찾을 수 없습니다."),
  ASSET_CONTENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 URL입니다."),
  CONTENT_COLLECTION_SOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "콘텐츠 수집 출처를 찾을 수 없습니다."),
  CONTENT_COLLECTION_SOURCE_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 콘텐츠 수집 출처입니다."),
  CONTENT_COLLECTION_SOURCE_INVALID(HttpStatus.BAD_REQUEST, "수집 플랫폼과 출처 URL이 일치하지 않습니다."),
  CONTENT_COLLECTION_ALREADY_RUNNING(HttpStatus.CONFLICT, "콘텐츠 전체 수집이 이미 진행 중입니다."),
  CONTENT_COLLECTION_CONTENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 수집 콘텐츠 URL입니다."),
  CONTENT_COLLECTION_POST_INVALID(HttpStatus.BAD_REQUEST, "등록한 출처와 게시물 URL이 일치하지 않습니다."),
  CONTENT_COLLECTION_REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "수집 콘텐츠 검수 항목을 찾을 수 없습니다."),
  CONTENT_COLLECTION_REVIEW_NOT_PENDING(HttpStatus.CONFLICT, "이미 처리된 수집 콘텐츠입니다."),
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
