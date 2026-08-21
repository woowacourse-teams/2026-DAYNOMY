package org.grit.daynomy.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.ErrorCode;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ErrorResponse {

  private final String code;
  private final String message;

  @JsonInclude(JsonInclude.Include.NON_EMPTY)
  private final List<ValidationErrorField> errors;

  public static ErrorResponse from(ErrorCode errorCode) {
    return new ErrorResponse(errorCode.code(), errorCode.message(), List.of());
  }

  public static ErrorResponse of(ErrorCode errorCode, List<ValidationErrorField> errors) {
    return new ErrorResponse(
        errorCode.code(), errorCode.message(), errors == null ? List.of() : List.copyOf(errors));
  }
}
