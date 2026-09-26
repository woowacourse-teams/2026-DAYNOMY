package org.grit.daynomy.common.logging;

public enum LogEvent {
  AI_NEWS_GENERATION_REQUESTED("ai.news_generation.requested"),
  AI_NEWS_GENERATION_COMPLETED("ai.news_generation.completed"),
  AI_NEWS_GENERATION_FAILED("ai.news_generation.failed"),
  AI_NEWS_VALIDATION_FAILED("ai.news_validation.failed"),
  AI_IMAGE_GENERATION_REQUESTED("ai.image_generation.requested"),
  AI_IMAGE_GENERATION_COMPLETED("ai.image_generation.completed"),
  AI_IMAGE_GENERATION_FAILED("ai.image_generation.failed");

  private final String code;

  LogEvent(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
