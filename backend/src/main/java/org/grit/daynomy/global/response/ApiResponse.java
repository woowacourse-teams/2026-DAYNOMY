package org.grit.daynomy.global.response;

public record ApiResponse<T>(String status, String code, String message, T body) {

  public static <T> ApiResponse<T> success(String message, T body) {
    return new ApiResponse<>("SUCCESS", null, message, body);
  }

  public static ApiResponse<Void> error(String code, String message) {
    return new ApiResponse<>("ERROR", code, message, null);
  }
}
