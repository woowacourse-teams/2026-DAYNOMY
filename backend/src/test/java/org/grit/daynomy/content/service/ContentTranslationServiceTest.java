package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.LocalDate;
import org.grit.daynomy.external.openai.ContentTranslation;
import org.grit.daynomy.external.openai.OpenAiContentTranslationException;
import org.grit.daynomy.external.openai.OpenAiContentTranslator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentTranslationServiceTest {

  @Mock private OpenAiContentTranslator translator;

  @InjectMocks private ContentTranslationService service;

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
  @DisplayName("영문 판별은 문자가 모두 라틴 문자인 경우로 제한한다")
  void englishDetectionIsConservative() {
    assertThat(ContentTranslationService.isEnglish("Federal Reserve holds rates")).isTrue();
    assertThat(ContentTranslationService.isEnglish("연준이 금리를 동결했다")).isFalse();
    assertThat(ContentTranslationService.isEnglish("Samsung 실적 발표")).isFalse();
    assertThat(ContentTranslationService.isEnglish("Market 東京")).isFalse();
  }
}
