package org.grit.daynomy.person.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FeaturedPersonErrorCode implements ErrorCode {
  FEATURED_PERSON_NOT_FOUND(HttpStatus.NOT_FOUND, "주요 인물을 찾을 수 없습니다."),
  FEATURED_PERSON_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 인물입니다."),
  FEATURED_PERSON_POST_NOT_FOUND(HttpStatus.NOT_FOUND, "주요 인물 게시물을 찾을 수 없습니다."),
  FEATURED_PERSON_POST_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 등록된 게시물 URL입니다."),
  FEATURED_PERSON_INVALID_SOCIAL_URL(HttpStatus.BAD_REQUEST, "계정 또는 게시물 URL이 올바르지 않습니다.");

  private final HttpStatus status;
  private final String message;

  FeaturedPersonErrorCode(HttpStatus status, String message) {
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
