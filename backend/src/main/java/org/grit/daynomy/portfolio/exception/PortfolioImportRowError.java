package org.grit.daynomy.portfolio.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public record PortfolioImportRowError(ErrorCode cause, int row) implements ErrorCode {
  @Override
  public String code() {
    return cause.code();
  }

  @Override
  public HttpStatus status() {
    return cause.status();
  }

  @Override
  public String message() {
    return row + "번째 거래: " + cause.message();
  }
}
