package org.grit.daynomy.news.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsGenerationTopicTest {

  @Test
  @DisplayName("준비된 주제는 예약 후 생성된 뉴스와 연결된다")
  void readyTopicIsReservedAndGenerated() {
    NewsGenerationTopic topic = topic();
    News news = News.createDraft("제목", "본문", null, List.of(), Category.STOCK);

    topic.reserve();
    topic.generated(news);

    assertThat(topic.getStatus()).isEqualTo(NewsGenerationTopicStatus.GENERATED);
    assertThat(topic.getNews()).isSameAs(news);
  }

  @Test
  @DisplayName("예약된 주제는 생성 실패 시 준비 상태로 복구된다")
  void reservedTopicIsReleased() {
    NewsGenerationTopic topic = topic();
    topic.reserve();

    topic.release();

    assertThat(topic.getStatus()).isEqualTo(NewsGenerationTopicStatus.READY);
  }

  @Test
  @DisplayName("이미 예약된 주제는 다시 예약할 수 없다")
  void reservedTopicCannotBeReservedAgain() {
    NewsGenerationTopic topic = topic();
    topic.reserve();

    assertThatThrownBy(topic::reserve).isInstanceOf(IllegalStateException.class);
  }

  private NewsGenerationTopic topic() {
    return NewsGenerationTopic.create(
        "a".repeat(64),
        NewsGenerationType.KOREA,
        "경제 뉴스",
        LocalDate.of(2026, 11, 6),
        Category.STOCK,
        List.of("사실 1", "사실 2", "사실 3"),
        List.of(
            new NewsSourceInfo("출처 1", "https://example.com/1"),
            new NewsSourceInfo("출처 2", "https://example.com/2")));
  }
}
