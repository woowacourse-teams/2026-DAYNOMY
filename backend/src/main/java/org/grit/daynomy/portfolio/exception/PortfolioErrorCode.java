package org.grit.daynomy.portfolio.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PortfolioErrorCode implements ErrorCode {
  PORTFOLIO_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스의 포트폴리오 분석을 찾을 수 없습니다."),
  DUPLICATE_PORTFOLIO_ASSET(HttpStatus.BAD_REQUEST, "동일한 포트폴리오 자산을 중복해서 요청할 수 없습니다."),
  INVALID_PORTFOLIO_WEIGHT_TOTAL(HttpStatus.BAD_REQUEST, "포트폴리오 보유 비중의 합은 100이어야 합니다.");

  private final HttpStatus status;
  private final String message;

  PortfolioErrorCode(HttpStatus status, String message) {
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
