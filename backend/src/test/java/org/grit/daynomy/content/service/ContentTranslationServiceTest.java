package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.openai.ContentTranslation;
import org.grit.daynomy.external.openai.OpenAiContentTranslationException;
import org.grit.daynomy.external.openai.OpenAiContentTranslator;
import org.grit.daynomy.external.openai.OpenAiHttpFailure;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.ResourceAccessException;

@ExtendWith(MockitoExtension.class)
class ContentTranslationServiceTest {

  @Mock private OpenAiContentTranslator translator;

  @InjectMocks private ContentTranslationService service;

  private final Logger logger = (Logger) LoggerFactory.getLogger(ContentTranslationService.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void setUpLogging() {
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDownLogging() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  @DisplayName("영문 외부 콘텐츠의 제목과 설명을 번역한다")
  void translateIfEnglishReturnsTranslatedItem() {
    CollectedContentItem item =
        new CollectedContentItem(
            "Market rallies",
            "Stocks closed higher.",
            "https://example.com/1",
            "https://example.com/1.png",
            LocalDate.of(2026, 11, 5));
    given(translator.translate(item.title(), item.description()))
        .willReturn(new ContentTranslation("시장 상승", "주식은 상승 마감했다."));

    CollectedContentItem translated = service.translateIfEnglish(item);

    assertThat(translated.title()).isEqualTo("시장 상승");
    assertThat(translated.description()).isEqualTo("주식은 상승 마감했다.");
    assertThat(translated.url()).isEqualTo(item.url());
    assertThat(translated.imageUrl()).isEqualTo(item.imageUrl());
    assertThat(translated.publishedDate()).isEqualTo(item.publishedDate());
  }

  @Test
  @DisplayName("한국어 외부 콘텐츠는 번역하지 않는다")
  void translateIfEnglishSkipsKorean() {
    CollectedContentItem item =
        new CollectedContentItem("시장 상승", "주식이 상승했다.", "https://example.com/1", null, null);

    assertThat(service.translateIfEnglish(item)).isSameAs(item);
    verifyNoInteractions(translator);
  }

  @Test
  @DisplayName("영어 제목만 번역하고 한국어 설명은 유지한다")
  void translateIfEnglishTranslatesOnlyEnglishTitle() {
    CollectedContentItem item =
        new CollectedContentItem(
            "Market rallies", "주식이 상승했다.", "https://example.com/1", null, null);
    given(translator.translate(item.title(), item.description()))
        .willReturn(new ContentTranslation("시장 상승", "변경된 설명"));

    CollectedContentItem translated = service.translateIfEnglish(item);

    assertThat(translated.title()).isEqualTo("시장 상승");
    assertThat(translated.description()).isEqualTo(item.description());
  }

  @Test
  @DisplayName("영어 설명만 번역하고 한국어 제목은 유지한다")
  void translateIfEnglishTranslatesOnlyEnglishDescription() {
    CollectedContentItem item =
        new CollectedContentItem(
            "시장 상승", "Stocks closed higher.", "https://example.com/1", null, null);
    given(translator.translate(item.title(), item.description()))
        .willReturn(new ContentTranslation("변경된 제목", "주식은 상승 마감했다."));

    CollectedContentItem translated = service.translateIfEnglish(item);

    assertThat(translated.title()).isEqualTo(item.title());
    assertThat(translated.description()).isEqualTo("주식은 상승 마감했다.");
  }

  @Test
  @DisplayName("번역 실패 시 원문을 유지한다")
  void translateIfEnglishKeepsOriginalOnFailure() {
    CollectedContentItem item =
        new CollectedContentItem(
            "Market rallies", "Stocks closed higher.", "https://example.com/1", null, null);
    willThrow(new OpenAiContentTranslationException("failed"))
        .given(translator)
        .translate(item.title(), item.description());

    assertThat(service.translateIfEnglish(item)).isSameAs(item);
  }

  @Test
  @DisplayName("번역 요청 실패 원인을 구조화 로그로 남긴다")
  void translateIfEnglishLogsFailureReason() {
    CollectedContentItem item =
        new CollectedContentItem(
            "Market rallies", "Stocks closed higher.", "https://example.com/1", null, null);
    willThrow(
            new OpenAiContentTranslationException(
                "REQUEST_FAILED", "failed", new ResourceAccessException("connection refused")))
        .given(translator)
        .translate(item.title(), item.description());

    service.translateIfEnglish(item);

    assertThat(keyValues(logEvent(LogEvent.EXTERNAL_API_FAILED)))
        .containsEntry("api", "OpenAI")
        .containsEntry("operation", "external-content-translation")
        .containsEntry("reasonCode", "REQUEST_FAILED")
        .containsEntry("errorType", ResourceAccessException.class.getSimpleName())
        .containsKey("durationMs");
  }

  @Test
  @DisplayName("OpenAI 공급자 오류 분류를 구조화 로그로 남긴다")
  void translateIfEnglishLogsProviderFailureCategory() {
    CollectedContentItem item =
        new CollectedContentItem(
            "Market rallies", "Stocks closed higher.", "https://example.com/1", null, null);
    OpenAiHttpFailure httpFailure =
        new OpenAiHttpFailure(
            OpenAiHttpFailure.Category.QUOTA_EXHAUSTED,
            429,
            "credit_balance_exhausted",
            "insufficient_quota",
            false,
            null);
    willThrow(
            new OpenAiContentTranslationException(
                "REQUEST_FAILED", "failed", new ResourceAccessException("failed"), httpFailure))
        .given(translator)
        .translate(item.title(), item.description());

    service.translateIfEnglish(item);

    assertThat(keyValues(logEvent(LogEvent.EXTERNAL_API_FAILED)))
        .containsEntry("failureCategory", "QUOTA_EXHAUSTED")
        .containsEntry("httpStatus", 429)
        .containsEntry("providerErrorCode", "credit_balance_exhausted")
        .containsEntry("providerErrorType", "insufficient_quota")
        .containsEntry("retryable", false);
  }

  @Test
  @DisplayName("영문 제목만 전달하면 한국어 제목을 반환한다")
  void translateTitleIfEnglishReturnsTranslatedTitle() {
    given(translator.translate("Official update", ""))
        .willReturn(new ContentTranslation("공식 소식", ""));

    assertThat(service.translateTitleIfEnglish("Official update")).isEqualTo("공식 소식");
  }

  @Test
  @DisplayName("한국어 제목만 전달하면 번역하지 않는다")
  void translateTitleIfEnglishSkipsKoreanTitle() {
    assertThat(service.translateTitleIfEnglish("공식 소식")).isEqualTo("공식 소식");
    verifyNoInteractions(translator);
  }

  @Test
  @DisplayName("제목 번역 실패 시 원문을 반환한다")
  void translateTitleIfEnglishKeepsOriginalOnFailure() {
    willThrow(new OpenAiContentTranslationException("failed"))
        .given(translator)
        .translate("Official update", "");

    assertThat(service.translateTitleIfEnglish("Official update")).isEqualTo("Official update");
  }

  @Test
  @DisplayName("영문 판별은 문자가 모두 라틴 문자인 경우로 제한한다")
  void englishDetectionIsConservative() {
    assertThat(ContentTranslationService.isEnglish("Federal Reserve holds rates")).isTrue();
    assertThat(ContentTranslationService.isEnglish("연준이 금리를 동결했다")).isFalse();
    assertThat(ContentTranslationService.isEnglish("Samsung 실적 발표")).isFalse();
    assertThat(ContentTranslationService.isEnglish("Market 東京")).isFalse();
  }

  private ILoggingEvent logEvent(LogEvent event) {
    return appender.list.stream()
        .filter(loggingEvent -> event.code().equals(keyValues(loggingEvent).get("event")))
        .findFirst()
        .orElseThrow();
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    Map<String, Object> values = new HashMap<>();
    loggingEvent.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
    return values;
  }
}
