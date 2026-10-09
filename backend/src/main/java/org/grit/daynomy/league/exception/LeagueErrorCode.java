package org.grit.daynomy.league.exception;

import org.grit.daynomy.common.exception.ErrorCode;
import org.springframework.http.HttpStatus;

public enum LeagueErrorCode implements ErrorCode {
  PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "공개 투자자 프로필을 찾을 수 없습니다."),
  PROFILE_REQUIRED(HttpStatus.BAD_REQUEST, "공개 프로필을 먼저 만들어 주세요."),
  DUPLICATE_DISPLAY_NAME(HttpStatus.CONFLICT, "이미 사용 중인 공개 닉네임입니다."),
  INVALID_LEAGUE_ENROLLMENT(HttpStatus.BAD_REQUEST, "프로필 공개 후 리그에 참여할 수 있습니다."),
  INVALID_LEAGUE_WEEK(HttpStatus.BAD_REQUEST, "최근 8주 안의 주차를 선택해 주세요."),
  INVALID_LEAGUE_ASSET(HttpStatus.BAD_REQUEST, "국내 상장 주식과 ETF만 거래할 수 있습니다."),
  TRANSACTION_NOT_FOUND(HttpStatus.NOT_FOUND, "투자 기록을 찾을 수 없습니다."),
  INVALID_TRANSACTION_TYPE(HttpStatus.BAD_REQUEST, "보유 판단은 판단 기록 API로 작성해 주세요."),
  DECISION_REQUEST_CONFLICT(HttpStatus.CONFLICT, "이미 사용한 요청 키입니다. 작성 내용을 다시 확인해 주세요."),
  SHARED_HOLDING_NOT_FOUND(HttpStatus.NOT_FOUND, "공유용 자산을 찾을 수 없습니다."),
  SHARED_IMPORT_CONFLICT(HttpStatus.CONFLICT, "이미 가져온 종목입니다. 변경 내용을 확인한 뒤 덮어쓰기를 선택해 주세요."),
  DUPLICATE_SHARED_ASSET(HttpStatus.BAD_REQUEST, "같은 종목을 중복해서 가져올 수 없습니다."),
  SELL_QUANTITY_EXCEEDED(HttpStatus.BAD_REQUEST, "보유수량보다 많이 매도할 수 없습니다."),
  FUTURE_TRANSACTION_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "미래 날짜의 거래는 기록할 수 없습니다."),
  DETAIL_NOT_PUBLIC(HttpStatus.NOT_FOUND, "이 사용자는 상세 투자 기록을 공개하지 않았습니다."),
  SELF_FOLLOW_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "자신의 프로필은 팔로우할 수 없습니다.");

  private final HttpStatus status;
  private final String message;

  LeagueErrorCode(HttpStatus status, String message) {
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
