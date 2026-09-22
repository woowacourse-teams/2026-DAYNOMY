package org.grit.daynomy.asset.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AssetErrorCode implements ErrorCode {
  STOCK_SYNC_ALREADY_RUNNING(HttpStatus.CONFLICT, "국내 주식 종목 동기화가 이미 진행 중입니다."),
  STOCK_MASTER_DATA_NOT_FOUND(HttpStatus.BAD_GATEWAY, "동기화할 국내 주식 종목 데이터를 찾지 못했습니다.");

  private final HttpStatus status;
  private final String message;

  AssetErrorCode(HttpStatus status, String message) {
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
