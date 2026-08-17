package org.grit.daynomy.common.api;

import org.grit.daynomy.common.exception.ErrorCode;

public record ApiResponse<T>(String status, String code, String message, T body) {

  public static <T> ApiResponse<T> success(String message, T body) {
    return new ApiResponse<>("SUCCESS", null, message, body);
  }

  public static ApiResponse<Void> error(ErrorCode errorCode) {
    return new ApiResponse<>("ERROR", errorCode.name(), errorCode.message(), null);
  }
}
