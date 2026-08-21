package org.grit.daynomy.market.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MarketErrorCode implements ErrorCode {
  MARKET_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스의 시장 분석을 찾을 수 없습니다.");

  private final HttpStatus status;
  private final String message;

  MarketErrorCode(HttpStatus status, String message) {
    this.status = status;
    this.message = message;
  }

  @Override
  public String code() {
    return name();
  }

  @Override
  public HttpStatus status() {
    return status;
  }

  @Override
  public String message() {
    return message;
  }
}
