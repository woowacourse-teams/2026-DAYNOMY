package org.grit.daynomy.common.logging;

public enum LogEvent {
  HTTP_REQUEST_STARTED("http.request.started", "HTTP 요청 시작"),
  HTTP_REQUEST_COMPLETED("http.request.completed", "HTTP 요청 완료"),

  NEWS_GENERATION_COMPLETED("news.generation.completed", "경제 뉴스 자동 생성 완료"),
  NEWS_DRAFT_CREATED("news.draft.created", "관리자 뉴스 초안 생성 완료"),
  NEWS_IMAGE_GENERATED("news.image.generated", "관리자 뉴스 이미지 생성 완료"),
  NEWS_PUBLISH_COMPLETED("news.publish.completed", "관리자 뉴스 발행 완료"),
  NEWS_REJECT_COMPLETED("news.reject.completed", "관리자 뉴스 거절 완료"),
  NEWS_UPDATE_COMPLETED("news.update.completed", "관리자 뉴스 수정 완료"),
  NEWS_DELETE_COMPLETED("news.delete.completed", "관리자 뉴스 삭제 완료"),
  NEWS_GENERATION_STARTED("news.generation.started", "경제 뉴스 예약 생성 시작"),
  NEWS_GENERATION_FAILED("news.generation.failed", "경제 뉴스 예약 생성 실패"),

  PORTFOLIO_CALCULATION_COMPLETED("portfolio.calculation.completed", "포트폴리오 계산 완료"),
  PORTFOLIO_ANALYSIS_REQUESTED("portfolio.analysis.requested", "포트폴리오 분석 요청"),
  PORTFOLIO_ANALYSIS_COMPLETED("portfolio.analysis.completed", "포트폴리오 분석 완료"),
  PORTFOLIO_ANALYSIS_FAILED("portfolio.analysis.failed", "포트폴리오 분석 실패"),

  MARKET_ANALYSIS_REQUESTED("market.analysis.requested", "시장 분석 요청"),
  MARKET_ANALYSIS_COMPLETED("market.analysis.completed", "시장 분석 완료"),
  MARKET_ANALYSIS_FAILED("market.analysis.failed", "시장 분석 실패"),

  KEYWORD_EXTRACTION_REQUESTED("keyword.extraction.requested", "키워드 추출 요청"),
  KEYWORD_EXTRACTION_COMPLETED("keyword.extraction.completed", "키워드 추출 완료"),
  KEYWORD_EXTRACTION_FAILED("keyword.extraction.failed", "키워드 추출 실패"),

  EXTERNAL_UPLOAD_FAILED("external.upload.failed", "외부 이미지 업로드 실패"),
  EXTERNAL_DELETE_FAILED("external.delete.failed", "외부 이미지 삭제 실패"),

  AI_NEWS_GENERATION_REQUESTED("ai.news_generation.requested", "OpenAI 뉴스 생성 요청"),
  AI_NEWS_GENERATION_COMPLETED("ai.news_generation.completed", "OpenAI 뉴스 생성 완료"),
  AI_NEWS_GENERATION_FAILED("ai.news_generation.failed", "OpenAI 뉴스 생성 실패"),
  AI_NEWS_VALIDATION_FAILED("ai.news_validation.failed", "생성 뉴스 검증 실패"),
  AI_IMAGE_GENERATION_REQUESTED("ai.image_generation.requested", "OpenAI 이미지 생성 요청"),
  AI_IMAGE_GENERATION_COMPLETED("ai.image_generation.completed", "OpenAI 이미지 생성 완료"),
  AI_IMAGE_GENERATION_FAILED("ai.image_generation.failed", "OpenAI 이미지 생성 실패");

  private final String code;
  private final String message;

  LogEvent(String code, String message) {
    this.code = code;
    this.message = message;
  }

  public String code() {
    return code;
  }

  public String message() {
    return message;
  }
}
