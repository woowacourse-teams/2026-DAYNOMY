package org.grit.daynomy.bookmark.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum BookmarkErrorCode implements ErrorCode {
  BOOKMARK_ALREADY_EXISTS(HttpStatus.CONFLICT, "이미 북마크한 자산입니다.");

  private final HttpStatus status;
  private final String message;

  BookmarkErrorCode(HttpStatus status, String message) {
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
