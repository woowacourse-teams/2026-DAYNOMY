package org.grit.daynomy.news.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BokNewsGenerationSchedulerTest {

  @Mock private NewsGenerationService newsGenerationService;

  @InjectMocks private BokNewsGenerationScheduler bokNewsGenerationScheduler;

  @Test
  @DisplayName("한국은행 ECOS 뉴스 생성을 실행한다")
  void generateBokNews() {
    bokNewsGenerationScheduler.generateBokNews();

    verify(newsGenerationService).generateBokNews();
  }
}
