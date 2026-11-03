package org.grit.daynomy.content.service;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.exception.ContentErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ContentCollectionSchedulerTest {

  @Mock private ContentCollectionBatchService batchService;

  @Test
  void delegatesToBatchCollection() {
    new ContentCollectionScheduler(batchService).collect();

    then(batchService).should().collectAllEnabled();
  }

  @Test
  void skipsWhenBatchCollectionIsAlreadyRunning() {
    given(batchService.collectAllEnabled())
        .willThrow(new BusinessException(ContentErrorCode.CONTENT_COLLECTION_ALREADY_RUNNING));

    new ContentCollectionScheduler(batchService).collect();

    then(batchService).should().collectAllEnabled();
  }
}
