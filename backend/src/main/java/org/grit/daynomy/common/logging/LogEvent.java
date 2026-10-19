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

  FEATURED_PERSON_CREATED("featured-person.created", "주요 인물 생성 완료"),
  FEATURED_PERSON_UPDATED("featured-person.updated", "주요 인물 수정 완료"),
  FEATURED_PERSON_DELETED("featured-person.deleted", "주요 인물 삭제 완료"),
  FEATURED_PERSON_POST_CREATED("featured-person.post.created", "주요 인물 게시물 생성 완료"),
  FEATURED_PERSON_POST_DELETED("featured-person.post.deleted", "주요 인물 게시물 삭제 완료"),
  FEATURED_PERSON_POST_COLLECTION_STARTED(
      "featured-person.post.collection.started", "주요 인물 게시물 수집 시작"),
  FEATURED_PERSON_POST_COLLECTION_COMPLETED(
      "featured-person.post.collection.completed", "주요 인물 게시물 수집 완료"),
  FEATURED_PERSON_POST_COLLECTION_SKIPPED(
      "featured-person.post.collection.skipped", "주요 인물 게시물 수집 스킵"),
  FEATURED_PERSON_POST_COLLECTION_FAILED(
      "featured-person.post.collection.failed", "주요 인물 게시물 수집 실패"),
  FEATURED_PERSON_POST_CANDIDATE_APPROVED(
      "featured-person.post-candidate.approved", "주요 인물 게시물 후보 승인 완료"),
  FEATURED_PERSON_POST_CANDIDATE_REJECTED(
      "featured-person.post-candidate.rejected", "주요 인물 게시물 후보 거절 완료"),
  FEATURED_PERSON_POST_CANDIDATE_PROCESSING_FAILED(
      "featured-person.post-candidate.processing.failed", "주요 인물 게시물 후보 처리 실패"),

  ASSET_CONTENT_SYNC_COMPLETED("asset-content.sync.completed", "종목 콘텐츠 연결 완료"),
  ASSET_CONTENT_CREATED("asset-content.created", "종목 콘텐츠 생성 완료"),
  ASSET_CONTENT_DELETED("asset-content.deleted", "종목 콘텐츠 삭제 완료"),

  FEEDBACK_CREATED("feedback.created", "피드백 생성 완료"),
  FEEDBACK_STATUS_UPDATED("feedback.status.updated", "피드백 상태 변경 완료"),
  FEEDBACK_DELETED("feedback.deleted", "피드백 삭제 완료"),

  PORTFOLIO_CALCULATION_STARTED("portfolio.calculation.started", "포트폴리오 계산 시작"),
  PORTFOLIO_CALCULATION_COMPLETED("portfolio.calculation.completed", "포트폴리오 계산 완료"),
  PORTFOLIO_ANALYSIS_REQUESTED("portfolio.analysis.requested", "포트폴리오 분석 요청"),
  PORTFOLIO_ANALYSIS_COMPLETED("portfolio.analysis.completed", "포트폴리오 분석 완료"),
  PORTFOLIO_ANALYSIS_CORRECTION_REQUESTED(
      "portfolio.analysis.correction_requested", "포트폴리오 분석 응답 교정 요청"),
  PORTFOLIO_ANALYSIS_FAILED("portfolio.analysis.failed", "포트폴리오 분석 실패"),
  PORTFOLIO_SNAPSHOT_COMPLETED("portfolio.snapshot.completed", "포트폴리오 일별 스냅샷 생성 완료"),
  PORTFOLIO_SNAPSHOT_FAILED("portfolio.snapshot.failed", "포트폴리오 일별 스냅샷 생성 실패"),
  LEAGUE_CALCULATION_FAILED("league.calculation.failed", "투자 리그 일별 수익률 집계 실패"),

  MARKET_ANALYSIS_REQUESTED("market.analysis.requested", "시장 분석 요청"),
  MARKET_ANALYSIS_COMPLETED("market.analysis.completed", "시장 분석 완료"),
  MARKET_ANALYSIS_FAILED("market.analysis.failed", "시장 분석 실패"),

  KEYWORD_EXTRACTION_REQUESTED("keyword.extraction.requested", "키워드 추출 요청"),
  KEYWORD_EXTRACTION_COMPLETED("keyword.extraction.completed", "키워드 추출 완료"),
  KEYWORD_EXTRACTION_FAILED("keyword.extraction.failed", "키워드 추출 실패"),

  EXTERNAL_UPLOAD_FAILED("external.upload.failed", "외부 이미지 업로드 실패"),
  EXTERNAL_UPLOAD_COMPLETED("external.upload.completed", "외부 이미지 업로드 완료"),
  EXTERNAL_DELETE_FAILED("external.delete.failed", "외부 이미지 삭제 실패"),
  EXTERNAL_DELETE_COMPLETED("external.delete.completed", "외부 이미지 삭제 완료"),
  EXTERNAL_API_REQUESTED("external.api.requested", "외부 API 호출 시작"),
  EXTERNAL_API_COMPLETED("external.api.completed", "외부 API 호출 완료"),
  EXTERNAL_API_FAILED("external.api.failed", "외부 API 호출 실패"),

  STOCK_PRICE_SYNC_STARTED("stock-price.sync.started", "종가 동기화 시작"),
  STOCK_PRICE_SYNC_COMPLETED("stock-price.sync.completed", "종가 동기화 완료"),
  STOCK_PRICE_SYNC_SKIPPED("stock-price.sync.skipped", "종가 동기화 스킵"),
  STOCK_PRICE_SYNC_FAILED("stock-price.sync.failed", "종가 동기화 실패"),
  MARKET_CALENDAR_SYNC_COMPLETED("market-calendar.sync.completed", "거래일 달력 동기화 완료"),
  MARKET_CALENDAR_SYNC_FAILED("market-calendar.sync.failed", "거래일 달력 동기화 실패"),

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
