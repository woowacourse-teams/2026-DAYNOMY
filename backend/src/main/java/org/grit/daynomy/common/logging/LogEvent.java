package org.grit.daynomy.common.logging;

public enum LogEvent {
  HTTP_REQUEST_STARTED("http.request.started", "HTTP 요청 시작"),
  HTTP_REQUEST_COMPLETED("http.request.completed", "HTTP 요청 완료"),
  NEWS_GENERATION_COMPLETED("news.generation.completed", "경제 뉴스 자동 생성 완료"),
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
