package org.grit.daynomy.common;

public record ErrorResponse(String code, String message) {

  public static ErrorResponse from(ErrorCode errorCode) {
    return new ErrorResponse(errorCode.code(), errorCode.message());
  }
}
