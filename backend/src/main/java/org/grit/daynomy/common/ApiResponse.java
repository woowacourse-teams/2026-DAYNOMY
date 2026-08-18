package org.grit.daynomy.common;

public record ApiResponse<T>(ApiStatus status, String message, T data) {

  public static <T> ApiResponse<T> success(String message, T data) {
    return new ApiResponse<>(ApiStatus.SUCCESS, message, data);
  }

  public static ApiResponse<Void> error(String message) {
    return new ApiResponse<>(ApiStatus.ERROR, message, null);
  }
}
