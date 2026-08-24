package org.grit.daynomy.portfolio.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PortfolioErrorCode implements ErrorCode {
  PORTFOLIO_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스의 포트폴리오 분석을 찾을 수 없습니다."),
  PORTFOLIO_ASSET_NOT_FOUND(HttpStatus.NOT_FOUND, "북마크한 자산을 찾을 수 없습니다.");

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
