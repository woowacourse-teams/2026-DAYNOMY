package org.grit.daynomy.common;

import org.springframework.http.HttpStatus;

public interface ErrorCode {

  String code();

  HttpStatus status();

  String message();
}
