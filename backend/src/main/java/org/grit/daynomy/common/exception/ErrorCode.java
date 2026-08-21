package org.grit.daynomy.common.exception;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

  String code();

  HttpStatus status();

  String message();
}
