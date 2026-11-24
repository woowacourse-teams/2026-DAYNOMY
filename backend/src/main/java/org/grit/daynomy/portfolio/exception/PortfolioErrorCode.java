package org.grit.daynomy.portfolio.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum PortfolioErrorCode implements ErrorCode {
  INVALID_IDEMPOTENCY_KEY(HttpStatus.BAD_REQUEST, "요청 키는 영문·숫자·마침표·밑줄·콜론·하이픈 1~128자로 입력해주세요."),
  IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "동일한 요청 키에 다른 거래 입력을 사용할 수 없습니다."),
  PORTFOLIO_TRANSACTION_BUSY(
      HttpStatus.SERVICE_UNAVAILABLE, "거래 처리를 기다리는 중입니다. 같은 요청 키로 다시 시도해주세요."),
  PORTFOLIO_ANALYSIS_SOURCE_DATE_UNCONFIRMED(
      HttpStatus.BAD_GATEWAY, "자료의 발표일을 확인하지 못했습니다. 기존 분석을 유지합니다."),
  PORTFOLIO_ANALYSIS_SOURCE_UNAVAILABLE(
      HttpStatus.BAD_GATEWAY, "분석 자료의 본문을 확인하지 못했습니다. 기존 분석을 유지합니다."),
  PORTFOLIO_ANALYSIS_RISK_SEARCH_FAILED(
      HttpStatus.BAD_GATEWAY, "반대 근거 검색·원문 확인에 실패했습니다. 기존 분석을 유지합니다."),
  INVALID_TRANSACTION_EXCHANGE_RATE(
      HttpStatus.BAD_REQUEST, "미국 거래는 1 USD당 원화 환율이 필요하며, 국내 거래의 환율은 1이어야 합니다."),
  PORTFOLIO_ANALYSIS_IN_PROGRESS(HttpStatus.CONFLICT, "포트폴리오 분석이 진행 중입니다."),
  PORTFOLIO_ANALYSIS_REFRESH_LIMIT_EXCEEDED(
      HttpStatus.TOO_MANY_REQUESTS, "새 소식 확인은 한국 시간 기준 하루 3회까지 가능합니다. 내일 다시 확인해주세요."),
  PORTFOLIO_ANALYSIS_TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 분석 작업을 찾을 수 없습니다."),
  PORTFOLIO_ANALYSIS_SESSION_REQUIRED(HttpStatus.CONFLICT, "분석 전에 비회원 세션을 준비해주세요."),
  PORTFOLIO_ANALYSIS_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "분석 요청이 많습니다. 잠시 후 다시 시도해주세요."),
  PORTFOLIO_ANALYSIS_REQUEST_EXPIRED(HttpStatus.CONFLICT, "분석 요청이 만료되었습니다. 다시 시도해주세요."),
  PORTFOLIO_ANALYSIS_ASSETS_CHANGED(HttpStatus.CONFLICT, "분석 대상 자산이 변경되었습니다. 다시 시도해주세요."),
  PORTFOLIO_ANALYSIS_ASSETS_UNAVAILABLE(HttpStatus.CONFLICT, "저장된 보유 자산과 가격이 있어야 분석할 수 있습니다."),
  PORTFOLIO_ANALYSIS_SHARE_NOT_READY(HttpStatus.CONFLICT, "완료된 포트폴리오 분석만 공유할 수 있습니다."),
  PORTFOLIO_ANALYSIS_SHARE_INVALID_ASSETS(HttpStatus.BAD_REQUEST, "공개할 분석 종목을 올바르게 선택해 주세요."),
  PORTFOLIO_ANALYSIS_SHARE_NOT_FOUND(HttpStatus.NOT_FOUND, "공유된 포트폴리오 분석을 찾을 수 없습니다."),
  PORTFOLIO_ANALYSIS_SHARE_EXPIRED(HttpStatus.GONE, "공유 링크가 만료되었습니다."),
  PORTFOLIO_ANALYSIS_SHARE_REVOKED(HttpStatus.GONE, "공유가 중단된 링크입니다."),
  PORTFOLIO_ANALYSIS_SHARE_OWNER_COMMENT_FORBIDDEN(
      HttpStatus.FORBIDDEN, "본인이 공유한 분석에는 댓글을 작성할 수 없습니다."),
  PORTFOLIO_ANALYSIS_COMMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "작성한 댓글을 찾을 수 없습니다."),
  PORTFOLIO_ANALYSIS_COMMENT_REPLY_NOT_FOUND(HttpStatus.NOT_FOUND, "작성한 대댓글을 찾을 수 없습니다."),
  PORTFOLIO_ANALYSIS_SHARE_RATE_LIMITED(
      HttpStatus.TOO_MANY_REQUESTS, "공유 분석 요청이 많습니다. 잠시 후 다시 시도해 주세요."),
  PORTFOLIO_ANALYSIS_NOT_FOUND(HttpStatus.NOT_FOUND, "해당 뉴스의 포트폴리오 분석을 찾을 수 없습니다."),
  PORTFOLIO_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오를 찾을 수 없습니다."),
  PORTFOLIO_HOLDING_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오 보유자산을 찾을 수 없습니다."),
  PORTFOLIO_TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "포트폴리오 거래를 찾을 수 없습니다."),
  INVALID_PORTFOLIO_PERIOD(HttpStatus.BAD_REQUEST, "조회 시작일은 종료일보다 늦을 수 없습니다."),
  INVALID_PORTFOLIO_TRADE_DATE(HttpStatus.BAD_REQUEST, "거래일은 해당 종목의 마지막 보유 수량 등록·조정일보다 이를 수 없습니다."),
  DUPLICATE_PORTFOLIO_ASSET(HttpStatus.BAD_REQUEST, "포트폴리오에 중복된 종목이 있습니다."),
  INSUFFICIENT_PORTFOLIO_QUANTITY(HttpStatus.BAD_REQUEST, "매도 수량이 해당 시점의 보유 수량보다 많습니다."),
  INVALID_PORTFOLIO_WEIGHT_TOTAL(HttpStatus.BAD_REQUEST, "포트폴리오 보유 비중의 합은 100이어야 합니다."),
  PORTFOLIO_ANALYSIS_TRIAL_EXHAUSTED(
      HttpStatus.FORBIDDEN, "비회원 포트폴리오 분석 체험을 이미 사용했습니다. 로그인 후 다시 이용해주세요.");

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
