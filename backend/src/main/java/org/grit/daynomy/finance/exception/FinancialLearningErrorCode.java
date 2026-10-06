package org.grit.daynomy.finance.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum FinancialLearningErrorCode implements ErrorCode {
  MOCK_TRADE_NOT_FOUND(HttpStatus.NOT_FOUND, "모의거래 기록을 찾을 수 없습니다."),
  MOCK_SELL_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST, "보유한 모의수량보다 많이 매도할 수 없습니다."),
  MOCK_TRADE_DELETE_INVALID(HttpStatus.BAD_REQUEST, "이 기록을 삭제하면 모의 보유수량이 음수가 됩니다."),
  INVALID_MOCK_TRADE_ASSET(HttpStatus.BAD_REQUEST, "국내 상장 주식과 ETF만 모의거래할 수 있습니다."),
  INVALID_MONTHLY_ALLOCATION(HttpStatus.BAD_REQUEST, "월 배분 합계가 사용 가능한 금액과 다릅니다."),
  INVALID_CHECK_IN_WEEK_START(HttpStatus.BAD_REQUEST, "주간 체크인의 시작일은 월요일이어야 합니다.");

  private final HttpStatus status;
  private final String message;

  FinancialLearningErrorCode(HttpStatus status, String message) {
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
