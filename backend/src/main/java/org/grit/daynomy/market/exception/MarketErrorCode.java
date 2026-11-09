package org.grit.daynomy.market.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum MarketErrorCode implements ErrorCode {
  MARKET_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스의 시장 분석을 찾을 수 없습니다."),
  MARKET_ANALYSIS_INVALID_JSON(HttpStatus.BAD_GATEWAY, "시장 분석 응답 형식이 올바르지 않습니다. 다시 발행해 주세요."),
  MARKET_ANALYSIS_EMPTY_TEXT(HttpStatus.BAD_GATEWAY, "시장 분석에 필요한 설명이 누락됐습니다. 다시 발행해 주세요."),
  MARKET_ANALYSIS_TEXT_TOO_LONG(HttpStatus.BAD_GATEWAY, "시장 분석 설명이 240자를 초과했습니다. 다시 발행해 주세요."),
  MARKET_ANALYSIS_INVALID_FORMAT(
      HttpStatus.BAD_GATEWAY, "시장 분석은 마크다운 없이 두 문장 이내여야 합니다. 다시 발행해 주세요."),
  MARKET_ANALYSIS_INVALID_TONE(HttpStatus.BAD_GATEWAY, "시장 분석의 문체가 해요체가 아닙니다. 다시 발행해 주세요."),
  MARKET_ANALYSIS_INVALID_EVIDENCE(
      HttpStatus.BAD_GATEWAY, "시장 분석의 근거를 기사 본문에서 확인할 수 없습니다. 다시 발행해 주세요."),
  MARKET_ANALYSIS_UNSUPPORTED_FACT(
      HttpStatus.BAD_GATEWAY, "시장 분석에 본문에 없는 수치나 날짜가 포함됐습니다. 다시 발행해 주세요.");

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
