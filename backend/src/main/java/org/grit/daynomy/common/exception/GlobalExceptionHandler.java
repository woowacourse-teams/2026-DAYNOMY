package org.grit.daynomy.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.common.logging.RequestLoggingFilter;
import org.grit.daynomy.common.logging.SanitizedLogException;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.common.response.ValidationErrorField;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  @ExceptionHandler(BusinessException.class)
  public ResponseEntity<ErrorResponse> handleBusinessException(
      BusinessException exception, HttpServletRequest request) {
    ErrorCode errorCode = exception.errorCode();
    request.setAttribute(RequestLoggingFilter.ERROR_CODE_ATTRIBUTE, errorCode.code());
    return ResponseEntity.status(errorCode.status()).body(ErrorResponse.from(errorCode));
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(
      ConstraintViolationException exception, HttpServletRequest request) {
    return invalidRequest(
        exception.getConstraintViolations().stream().map(this::toValidationErrorField).toList(),
        request);
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentTypeMismatch(
      MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
    String reason =
        exception.getRequiredType() != null && exception.getRequiredType().isEnum()
            ? "지원하지 않는 값입니다."
            : "타입이 올바르지 않습니다.";
    return invalidRequest(List.of(ValidationErrorField.of(exception.getName(), reason)), request);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(
      Exception exception, HttpServletRequest request) {
    ErrorCode errorCode = CommonErrorCode.INTERNAL_SERVER_ERROR;
    request.setAttribute(RequestLoggingFilter.ERROR_CODE_ATTRIBUTE, errorCode.code());
    log.atError()
        .setCause(SanitizedLogException.from(exception))
        .addKeyValue("event", LogEvent.HTTP_REQUEST_FAILED.code())
        .addKeyValue("errorCode", errorCode.code())
        .addKeyValue("errorType", exception.getClass().getSimpleName())
        .addKeyValue("method", request.getMethod())
        .addKeyValue("uri", request.getRequestURI())
        .log(LogEvent.HTTP_REQUEST_FAILED.message());
    return ResponseEntity.status(errorCode.status()).body(ErrorResponse.from(errorCode));
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ValidationErrorField> errors =
        exception.getBindingResult().getFieldErrors().stream()
            .map(this::toValidationErrorField)
            .toList();

    request.setAttribute(
        RequestLoggingFilter.ERROR_CODE_ATTRIBUTE,
        CommonErrorCode.INVALID_REQUEST.code(),
        WebRequest.SCOPE_REQUEST);
    return super.handleExceptionInternal(
        exception,
        ErrorResponse.of(CommonErrorCode.INVALID_REQUEST, errors),
        headers,
        status,
        request);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ValidationErrorField> errors =
        exception.getParameterValidationResults().stream()
            .map(this::toValidationErrorFields)
            .flatMap(List::stream)
            .toList();

    request.setAttribute(
        RequestLoggingFilter.ERROR_CODE_ATTRIBUTE,
        CommonErrorCode.INVALID_REQUEST.code(),
        WebRequest.SCOPE_REQUEST);
    return super.handleExceptionInternal(
        exception,
        ErrorResponse.of(CommonErrorCode.INVALID_REQUEST, errors),
        headers,
        status,
        request);
  }

  @Override
  protected ResponseEntity<Object> handleHttpMessageNotReadable(
      HttpMessageNotReadableException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    List<ValidationErrorField> errors =
        List.of(ValidationErrorField.of("requestBody", "요청 본문을 읽을 수 없습니다."));

    request.setAttribute(
        RequestLoggingFilter.ERROR_CODE_ATTRIBUTE,
        CommonErrorCode.INVALID_REQUEST.code(),
        WebRequest.SCOPE_REQUEST);
    return super.handleExceptionInternal(
        exception,
        ErrorResponse.of(CommonErrorCode.INVALID_REQUEST, errors),
        headers,
        status,
        request);
  }

  private ResponseEntity<ErrorResponse> invalidRequest(
      List<ValidationErrorField> errors, HttpServletRequest request) {
    ErrorCode errorCode = CommonErrorCode.INVALID_REQUEST;
    request.setAttribute(RequestLoggingFilter.ERROR_CODE_ATTRIBUTE, errorCode.code());
    return ResponseEntity.status(errorCode.status()).body(ErrorResponse.of(errorCode, errors));
  }

  private ValidationErrorField toValidationErrorField(FieldError fieldError) {
    return ValidationErrorField.of(fieldError.getField(), fieldError.getDefaultMessage());
  }

  private List<ValidationErrorField> toValidationErrorFields(ParameterValidationResult result) {
    String field = result.getMethodParameter().getParameterName();
    return result.getResolvableErrors().stream()
        .map(MessageSourceResolvable::getDefaultMessage)
        .map(reason -> ValidationErrorField.of(field, reason))
        .toList();
  }

  private ValidationErrorField toValidationErrorField(ConstraintViolation<?> violation) {
    return ValidationErrorField.of(
        fieldName(violation.getPropertyPath().toString()), violation.getMessage());
  }

  private String fieldName(String propertyPath) {
    int separatorIndex = propertyPath.lastIndexOf('.');
    if (separatorIndex < 0) {
      return propertyPath;
    }
    return propertyPath.substring(separatorIndex + 1);
  }
}
