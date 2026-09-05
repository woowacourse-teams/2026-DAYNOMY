package org.grit.daynomy.asset.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum AssetErrorCode implements ErrorCode {
  ASSET_RANKING_SYNC_ALREADY_RUNNING(HttpStatus.CONFLICT, "자산 순위 동기화가 이미 진행 중입니다.");

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
