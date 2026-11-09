package org.grit.daynomy.news.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.news.exception.NewsErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NewsTest {

  @Test
  @DisplayName("뉴스 초안은 빈 이미지 출처와 초안 상태로 생성된다")
  void createDraftCreatesDraftWithEmptyImageSource() {
    News news = News.createDraft("제목", "본문", null, sources(), Category.STOCK);

    assertThat(news.getImageSource()).isEqualTo(ImageSourceInfo.empty());
    assertThat(news.getStatus()).isEqualTo(NewsStatus.DRAFT);
    assertThat(news.getPublishedAt()).isNull();
    assertThat(news.isDraft()).isTrue();
    assertThat(news.isPublished()).isFalse();
  }

  @Test
  @DisplayName("뉴스 내용을 수정해도 기존 상태는 유지된다")
  void updateChangesContentWithoutChangingStatus() {
    Instant publishedAt = Instant.parse("2026-11-06T00:00:00Z");
    News news =
        News.createPublished("기존 제목", "기존 본문", null, sources(), Category.STOCK, publishedAt);
    ImageSourceInfo imageSource =
        ImageSourceInfo.wikimedia(
            "Wikimedia Commons", "https://example.com/image", "작성자", "CC BY", "");

    news.update(
        "수정 제목", "수정 본문", "https://example.com/new.webp", imageSource, sources(), Category.ETF);

    assertThat(news.getTitle()).isEqualTo("수정 제목");
    assertThat(news.getContent()).isEqualTo("수정 본문");
    assertThat(news.getImageUrl()).isEqualTo("https://example.com/new.webp");
    assertThat(news.getImageSource()).isEqualTo(imageSource);
    assertThat(news.getCategory()).isEqualTo(Category.ETF);
    assertThat(news.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
    assertThat(news.getPublishedAt()).isEqualTo(publishedAt);
  }

  @Test
  @DisplayName("이미지만 수정할 때 출처가 없으면 빈 출처로 저장된다")
  void updateImageUsesEmptySourceWhenMissing() {
    News news = News.createDraft("제목", "본문", null, sources(), Category.STOCK);

    news.updateImage("https://example.com/image.webp", null);

    assertThat(news.getImageUrl()).isEqualTo("https://example.com/image.webp");
    assertThat(news.getImageSource()).isEqualTo(ImageSourceInfo.empty());
  }

  @Test
  @DisplayName("초안 뉴스는 발행 상태로 변경된다")
  void publishChangesDraftToPublished() {
    News news = News.createDraft("제목", "본문", null, sources(), Category.STOCK);

    news.publish();

    assertThat(news.getStatus()).isEqualTo(NewsStatus.PUBLISHED);
    assertThat(news.getPublishedAt()).isNotNull();
    assertThat(news.isPublished()).isTrue();
  }

  @Test
  @DisplayName("초안이 아닌 뉴스는 발행하거나 거절할 수 없다")
  void nonDraftCannotBePublishedOrRejected() {
    News news =
        News.createPublished(
            "제목", "본문", null, sources(), Category.STOCK, Instant.parse("2026-11-06T00:00:00Z"));

    assertNotDraftException(news::publish);
    assertNotDraftException(news::reject);
  }

  @Test
  @DisplayName("초안 뉴스는 거절 상태로 변경된다")
  void rejectChangesDraftToRejected() {
    News news = News.createDraft("제목", "본문", null, sources(), Category.STOCK);

    news.reject();

    assertThat(news.getStatus()).isEqualTo(NewsStatus.REJECTED);
    assertThat(news.getPublishedAt()).isNull();
  }

  @Test
  @DisplayName("발행 뉴스는 삭제 시 발행 시간이 제거된다")
  void deleteChangesPublishedNewsToDeleted() {
    News news =
        News.createPublished(
            "제목", "본문", null, sources(), Category.STOCK, Instant.parse("2026-11-06T00:00:00Z"));

    news.delete();

    assertThat(news.getStatus()).isEqualTo(NewsStatus.DELETED);
    assertThat(news.getPublishedAt()).isNull();
  }

  private void assertNotDraftException(Runnable action) {
    assertThatThrownBy(action::run)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception -> assertThat(exception.errorCode()).isEqualTo(NewsErrorCode.NEWS_NOT_DRAFT));
  }

  private List<NewsSourceInfo> sources() {
    return List.of(new NewsSourceInfo("출처", "https://example.com/news"));
  }
}
