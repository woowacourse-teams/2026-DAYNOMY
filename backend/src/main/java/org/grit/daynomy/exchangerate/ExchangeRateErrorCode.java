package org.grit.daynomy.exchangerate;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum ExchangeRateErrorCode implements ErrorCode {
  INVALID_EXCHANGE_RATE_DATE(HttpStatus.BAD_REQUEST, "환율 조회일은 2000-01-01부터 오늘까지여야 합니다."),
  EXCHANGE_RATE_RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "환율 조회 요청이 많습니다. 잠시 후 다시 시도해 주세요."),
  EXCHANGE_RATE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "해당 날짜의 USD/KRW 환율을 가져오지 못했습니다.");
  private final HttpStatus status;
  private final String message;

  ExchangeRateErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  public String code() {
    return name();
  }

  public HttpStatus status() {
    return status;
  }

  public String message() {
    return message;
  }
}
