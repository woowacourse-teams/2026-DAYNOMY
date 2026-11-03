package org.grit.daynomy.content.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentCollectionSource;
import org.grit.daynomy.content.dto.ContentCollectionResultResponse;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.grit.daynomy.content.repository.ContentCollectionSourceRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentCollectionBatchServiceTest {

  @Mock private ContentCollectionSourceRepository sourceRepository;
  @Mock private ContentCollectionService collectionService;

  @Test
  void oneSourceFailureDoesNotStopRemainingSources() {
    ContentCollectionSource first = org.mockito.Mockito.mock(ContentCollectionSource.class);
    ContentCollectionSource second = org.mockito.Mockito.mock(ContentCollectionSource.class);
    given(first.getId()).willReturn(1L);
    given(second.getId()).willReturn(2L);
    given(sourceRepository.findAllByEnabledTrueOrderByIdAsc()).willReturn(List.of(first, second));
    given(collectionService.collect(1L))
        .willThrow(new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_INVALID));
    given(collectionService.collect(2L))
        .willReturn(new ContentCollectionResultResponse(2L, 4, 3, 1, 2));

    var result =
        new ContentCollectionBatchService(sourceRepository, collectionService).collectAllEnabled();

    assertThat(result.sourceCount()).isEqualTo(2);
    assertThat(result.successCount()).isEqualTo(1);
    assertThat(result.failureCount()).isEqualTo(1);
    assertThat(result.fetchedCount()).isEqualTo(4);
    assertThat(result.savedCount()).isEqualTo(3);
    assertThat(result.duplicateCount()).isEqualTo(1);
    assertThat(result.linkedCount()).isEqualTo(2);
    then(collectionService).should().collect(1L);
    then(collectionService).should().collect(2L);
  }

  @Test
  void rejectsConcurrentBatchCollection() throws Exception {
    ContentCollectionSource source = org.mockito.Mockito.mock(ContentCollectionSource.class);
    given(source.getId()).willReturn(1L);
    given(sourceRepository.findAllByEnabledTrueOrderByIdAsc()).willReturn(List.of(source));
    CountDownLatch entered = new CountDownLatch(1);
    CountDownLatch release = new CountDownLatch(1);
    given(collectionService.collect(1L))
        .willAnswer(
            invocation -> {
              entered.countDown();
              release.await(3, TimeUnit.SECONDS);
              return new ContentCollectionResultResponse(1L, 0, 0, 0, 0);
            });
    ContentCollectionBatchService service =
        new ContentCollectionBatchService(sourceRepository, collectionService);
    try (var executor = Executors.newSingleThreadExecutor()) {
      var firstRun = executor.submit(service::collectAllEnabled);
      assertThat(entered.await(3, TimeUnit.SECONDS)).isTrue();

      assertThatThrownBy(service::collectAllEnabled)
          .isInstanceOf(BusinessException.class)
          .extracting(exception -> ((BusinessException) exception).errorCode())
          .isEqualTo(ContentErrorCode.CONTENT_COLLECTION_ALREADY_RUNNING);

      release.countDown();
      assertThat(firstRun.get(3, TimeUnit.SECONDS).successCount()).isEqualTo(1);
    } finally {
      release.countDown();
    }
  }
}
