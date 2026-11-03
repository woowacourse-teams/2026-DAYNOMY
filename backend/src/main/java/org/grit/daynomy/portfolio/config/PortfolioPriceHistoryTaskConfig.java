package org.grit.daynomy.portfolio.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PortfolioPriceHistoryTaskConfig {

  @Bean
  ThreadPoolTaskExecutor portfolioPriceHistoryTaskExecutor() {
    var executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("portfolio-price-history-");
    // 종목별 과거 수집은 예약 배치와 분리하되 동시 조회와 대기 작업의 메모리를 제한한다.
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(32);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    return executor;
  }
}
