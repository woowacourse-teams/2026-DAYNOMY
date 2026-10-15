package org.grit.daynomy.news.service;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UsEconomyNewsGenerationSchedulerTest {

  @Mock private NewsGenerationService newsGenerationService;

  @InjectMocks private UsEconomyNewsGenerationScheduler scheduler;

  @Test
  void scheduledUsGenerationTriggersOnlyUsNews() {
    scheduler.generateUsEconomicNewsDrafts();

    verify(newsGenerationService).generateUsEconomicNewsDrafts();
  }
}
