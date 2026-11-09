package org.grit.daynomy.support;

import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.Map;
import javax.sql.DataSource;
import org.grit.daynomy.content.service.ContentInteractionRateLimiter;
import org.grit.daynomy.exchangerate.service.ExchangeRateRequestLimiter;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.grit.daynomy.league.service.LeagueRankingCache;
import org.grit.daynomy.league.service.LeagueReviewGuideRateLimiter;
import org.grit.daynomy.portfolio.service.PortfolioPriceHistoryService;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;
import org.springframework.test.util.ReflectionTestUtils;

public class DatabaseIsolationListener extends AbstractTestExecutionListener {
  @Override
  public int getOrder() {
    // Run cleanup before Spring opens a test transaction (order 4000).
    return 3500;
  }

  @Override
  public void beforeTestMethod(TestContext testContext) throws Exception {
    var context = testContext.getApplicationContext();
    DatabaseCleaner.reset(context.getBean(DataSource.class));
    clearMaps(context, PortfolioPriceHistoryService.class, "jobs", "refreshedRanges", "assetLocks");
    clearMaps(context, ExchangeRateService.class, "unavailableUntil");
    context
        .getBeansOfType(ExchangeRateRequestLimiter.class)
        .values()
        .forEach(
            limiter ->
                ((java.util.Collection<?>) ReflectionTestUtils.getField(limiter, "requests"))
                    .clear());
    clearMaps(context, ContentInteractionRateLimiter.class, "requests");
    clearMaps(context, LeagueReviewGuideRateLimiter.class, "requests");
    context
        .getBeansOfType(LeagueRankingCache.class)
        .values()
        .forEach(LeagueRankingCache::invalidateAfterCommit);
  }

  @Override
  public void afterTestMethod(TestContext testContext) {
    // A completed HTTP request may still have queued background writes.
    testContext
        .getApplicationContext()
        .getBeansOfType(ThreadPoolTaskExecutor.class)
        .values()
        .forEach(
            executor ->
                await()
                    .pollDelay(Duration.ZERO)
                    .atMost(Duration.ofSeconds(30))
                    .until(
                        () ->
                            executor.getActiveCount() == 0
                                && executor.getThreadPoolExecutor().getQueue().isEmpty()));
  }

  private static void clearMaps(ApplicationContext context, Class<?> type, String... fields) {
    context
        .getBeansOfType(type)
        .values()
        .forEach(
            bean -> {
              for (String field : fields) {
                ((Map<?, ?>) ReflectionTestUtils.getField(bean, field)).clear();
              }
            });
  }
}
