package org.grit.daynomy.portfolio.v2.config;

import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

@Configuration
public class PortfolioAnalysisAsyncV2Config {
  @Bean("portfolioAnalysisExecutorV2")
  SimpleAsyncTaskExecutor analysisExecutor() {
    var executor = new SimpleAsyncTaskExecutor("portfolio-analysis-v2-");
    executor.setVirtualThreads(true);
    var propagation = new ContextPropagatingTaskDecorator();
    executor.setTaskDecorator(
        task -> {
          var loggingContext = MDC.getCopyOfContextMap();
          return propagation.decorate(
              () -> {
                var previous = MDC.getCopyOfContextMap();
                try {
                  if (loggingContext != null) MDC.setContextMap(loggingContext);
                  task.run();
                } finally {
                  if (previous == null) MDC.clear();
                  else MDC.setContextMap(previous);
                }
              });
        });
    return executor;
  }
}
