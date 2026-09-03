package org.grit.daynomy.news.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DartNewsGenerationSchedulerTest {

  @Mock private NewsGenerationService newsGenerationService;

  @InjectMocks private DartNewsGenerationScheduler dartNewsGenerationScheduler;

  @Test
  @DisplayName("DART 뉴스 생성 스케줄러는 예약 실행을 서비스에 위임한다")
  void generateDartNews() {
    dartNewsGenerationScheduler.generateDartNews();

    verify(newsGenerationService).generateScheduledDartNews();
  }
}
