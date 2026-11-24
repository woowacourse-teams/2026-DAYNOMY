package org.grit.daynomy.news.config;

import org.grit.daynomy.deployment.DeploymentTaskExecutor;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class NewsPublishAiTaskConfig {

  @Bean("newsPublishAiExecutor")
  ThreadPoolTaskExecutor newsPublishAiExecutor() {
    var executor = new DeploymentTaskExecutor();
    executor.setThreadNamePrefix("news-publish-ai-");
    executor.setCorePoolSize(2);
    executor.setMaxPoolSize(2);
    // shortcut: 관리자 발행 대기열은 32건으로 제한, 동시 발행이 늘면 429 응답 정책을 추가한다.
    executor.setQueueCapacity(32);
    executor.setWaitForTasksToCompleteOnShutdown(true);
    executor.setAwaitTerminationSeconds(30);
    executor.setTaskDecorator(
        task -> {
          var context = MDC.getCopyOfContextMap();
          return () -> {
            var previous = MDC.getCopyOfContextMap();
            try {
              if (context == null) MDC.clear();
              else MDC.setContextMap(context);
              task.run();
            } finally {
              if (previous == null) MDC.clear();
              else MDC.setContextMap(previous);
            }
          };
        });
    return executor;
  }
}
