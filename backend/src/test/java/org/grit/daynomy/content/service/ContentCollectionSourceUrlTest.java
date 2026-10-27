package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.junit.jupiter.api.Test;

class ContentCollectionSourceUrlTest {

  @Test
  void normalizesSupportedProfileUrls() {
    assertThat(
            ContentCollectionSourceUrl.normalize(
                ContentSourceType.YOUTUBE, "http://www.youtube.com/@daynomy/?view=1"))
        .isEqualTo("https://www.youtube.com/@daynomy");
    assertThat(ContentCollectionSourceUrl.normalize(ContentSourceType.X, "https://x.com/daynomy/"))
        .isEqualTo("https://x.com/daynomy");
    assertThat(
            ContentCollectionSourceUrl.normalize(
                ContentSourceType.THREADS, "https://www.threads.net/@daynomy"))
        .isEqualTo("https://www.threads.net/@daynomy");
  }

  @Test
  void rejectsPostUrlAndMismatchedPlatform() {
    assertThatThrownBy(
            () ->
                ContentCollectionSourceUrl.normalize(
                    ContentSourceType.X, "https://x.com/daynomy/status/123"))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(ContentErrorCode.CONTENT_COLLECTION_SOURCE_INVALID);
    assertThatThrownBy(
            () ->
                ContentCollectionSourceUrl.normalize(
                    ContentSourceType.YOUTUBE, "https://x.com/daynomy"))
        .isInstanceOf(BusinessException.class);
  }
}
