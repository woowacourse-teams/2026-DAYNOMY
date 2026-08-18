package org.grit.daynomy.common;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleApiException(BusinessException exception) {
    ErrorCode errorCode = exception.errorCode();
    return ResponseEntity.status(errorCode.status()).body(ErrorResponse.from(errorCode));
  }

  @ExceptionHandler({
    IllegalArgumentException.class,
    MethodArgumentTypeMismatchException.class,
    MethodArgumentNotValidException.class
  })
  public ResponseEntity<ErrorResponse> handleBadRequest(Exception exception) {
    ErrorCode errorCode = ErrorCode.INVALID_REQUEST;
    return ResponseEntity.status(errorCode.status()).body(ErrorResponse.from(errorCode));
  }
}
