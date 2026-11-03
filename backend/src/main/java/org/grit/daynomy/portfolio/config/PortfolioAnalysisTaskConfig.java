package org.grit.daynomy.portfolio.config;

import java.util.Map;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class PortfolioAnalysisTaskConfig {

  @Bean("portfolioAnalysisTaskExecutor")
  ThreadPoolTaskExecutor analysisTaskExecutor(
      @Value("${portfolio.analysis.max-concurrent-tasks:4}") int concurrency) {
    var executor = new ThreadPoolTaskExecutor();
    executor.setThreadNamePrefix("portfolio-analysis-task-");
    executor.setCorePoolSize(concurrency);
    executor.setMaxPoolSize(concurrency);
    // 실행되지 않은 작업이 대기열에서 요청 유효 시간을 소진하지 않도록 한다.
    executor.setQueueCapacity(0);
    executor.setAllowCoreThreadTimeOut(true);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    var propagation = new ContextPropagatingTaskDecorator();
    executor.setTaskDecorator(
        task -> {
          var context = MDC.getCopyOfContextMap();
          return propagation.decorate(
              () -> {
                var previous = MDC.getCopyOfContextMap();
                try {
                  MDC.setContextMap(context == null ? Map.of() : context);
                  task.run();
                } finally {
                  MDC.setContextMap(previous == null ? Map.of() : previous);
                }
              });
        });
    return executor;
  }
}
