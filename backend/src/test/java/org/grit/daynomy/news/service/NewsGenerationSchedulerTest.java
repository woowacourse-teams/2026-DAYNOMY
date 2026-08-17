package org.grit.daynomy.news.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NewsGenerationSchedulerTest {

  @Mock private NewsGenerationService newsGenerationService;

  @InjectMocks private NewsGenerationScheduler newsGenerationScheduler;

  @Test
  @DisplayName("DART 뉴스 생성을 유가증권과 코스닥 주요사항보고서 대상으로 실행한다")
  void generateDartNews() {
    newsGenerationScheduler.generateDartNews();

    verify(newsGenerationService)
        .generateDartNews(any(LocalDate.class), any(LocalDate.class), eq("B"), eq("Y"));
    verify(newsGenerationService)
        .generateDartNews(any(LocalDate.class), any(LocalDate.class), eq("B"), eq("K"));
  }
}
