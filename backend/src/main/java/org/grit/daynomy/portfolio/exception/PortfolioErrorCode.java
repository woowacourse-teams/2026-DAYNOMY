package org.grit.daynomy.portfolio.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PortfolioErrorCode implements ErrorCode {
  PORTFOLIO_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스의 포트폴리오 분석을 찾을 수 없습니다."),
  PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오를 찾을 수 없습니다."),
  PORTFOLIO_HOLDING_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오 보유자산을 찾을 수 없습니다."),
  PORTFOLIO_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오 거래를 찾을 수 없습니다."),
  INVALID_PORTFOLIO_PERIOD(HttpStatus.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다."),
  INVALID_PORTFOLIO_TRADE_DATE(HttpStatus.BAD_REQUEST, "거래일은 해당 종목의 마지막 보유 수량 등록·조정일보다 이를 수 없습니다."),
  DUPLICATE_PORTFOLIO_ASSET(HttpStatus.BAD_REQUEST, "포트폴리오에 중복된 종목이 있습니다."),
  INSUFFICIENT_PORTFOLIO_QUANTITY(HttpStatus.BAD_REQUEST, "매도 수량이 해당 시점의 보유 수량보다 많습니다."),
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
