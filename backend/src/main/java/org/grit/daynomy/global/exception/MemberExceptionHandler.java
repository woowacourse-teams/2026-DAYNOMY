package org.grit.daynomy.global.exception;

import org.grit.daynomy.global.response.ApiResponse;
import org.grit.daynomy.member.controller.MemberController;
import org.grit.daynomy.member.exception.MemberNotFoundException;
import org.grit.daynomy.member.exception.NicknameAlreadyExistsException;
import org.grit.daynomy.member.exception.WithdrawnMemberException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = MemberController.class)
public class MemberExceptionHandler {

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleInvalidInput(
      MethodArgumentNotValidException exception) {
    return ResponseEntity.badRequest().body(ApiResponse.error("INVALID_INPUT", "입력값이 올바르지 않습니다."));
  }

  @ExceptionHandler(NicknameAlreadyExistsException.class)
  public ResponseEntity<ApiResponse<Void>> handleNicknameAlreadyExists(
      NicknameAlreadyExistsException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(ApiResponse.error("NICKNAME_ALREADY_EXISTS", "이미 사용 중인 닉네임입니다."));
  }

  @ExceptionHandler({MemberNotFoundException.class, WithdrawnMemberException.class})
  public ResponseEntity<ApiResponse<Void>> handleInvalidMember(RuntimeException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(ApiResponse.error("INVALID_TOKEN", "유효하지 않은 인증 정보입니다."));
  }
}
