package org.grit.daynomy.emailsubscription.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy;
import org.grit.daynomy.emailsubscription.repository.EmailAnalysisExecutionRepository;
import org.grit.daynomy.emailsubscription.repository.EmailAnalysisExecutionRepository.Job;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisCall;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

@Component
@Slf4j
@ConditionalOnProperty(name = "email.analysis.enabled", havingValue = "true")
public class EmailAnalysisScheduler {
  private static final ObjectMapper MAPPER = new ObjectMapper();
  private final EmailSubscriptionRepository subscriptions;
  private final ImportantStockSelectionService selection;
  private final EmailStockAnalysisService analysis;
  private final EmailAnalysisExecutionRepository executions;
  private final EmailAnalysisPolicy policy;
  private final Clock clock;

  @Autowired
  public EmailAnalysisScheduler(
      EmailSubscriptionRepository subscriptions,
      ImportantStockSelectionService selection,
      EmailStockAnalysisService analysis,
      EmailAnalysisExecutionRepository executions,
      EmailAnalysisPolicy policy) {
    this(subscriptions, selection, analysis, executions, policy, Clock.systemUTC());
  }

  EmailAnalysisScheduler(
      EmailSubscriptionRepository subscriptions,
      ImportantStockSelectionService selection,
      EmailStockAnalysisService analysis,
      EmailAnalysisExecutionRepository executions,
      EmailAnalysisPolicy policy,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.selection = selection;
    this.analysis = analysis;
    this.executions = executions;
    this.policy = policy;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
  public void analyzeDaily() {
    if (!policy.canStart(clock.instant())) return;
    var date = clock.instant().atZone(EmailAnalysisPolicy.ZONE).toLocalDate();
    var asOf = date.atTime(7, 0).atZone(EmailAnalysisPolicy.ZONE).toInstant();
    long after = 0;
    while (policy.canStart(clock.instant())) {
      var members = subscriptions.findEligibleMemberIds(after, PageRequest.of(0, 500));
      if (members.isEmpty()) break;
      var ids = new LinkedHashSet<Long>();
      for (Long member : members) {
        if (!policy.canStart(clock.instant())) break;
        selection
            .select(member, date)
            .ifPresent(
                selected ->
                    selected.stocks().stream().limit(3).forEach(stock -> ids.add(stock.assetId())));
      }
      executions.register(date, asOf, ids.stream().toList());
      after = members.getLast();
    }
    while (policy.canStart(clock.instant())) {
      var job = executions.claim(date, clock.instant(), policy);
      if (job.isEmpty()) return;
      process(job.get());
    }
  }

  private void process(Job job) {
    try {
      for (int attempt = 0; attempt < 2 && policy.canStart(clock.instant()); attempt++) {
        if (!executions.hasSubscriberHolding(job.assetId())) {
          executions.finish(job.id(), "SKIPPED", null, clock.instant());
          return;
        }
        executions.startAttempt(job.id());
        var accounting = new CallAccounting(job);
        var result =
            PortfolioAnalysisCall.observe(
                accounting, () -> analysis.analyzeStock(job.assetId(), job.asOf()));
        // 마감 후 끝난 결과는 보존하지만 당일 사용 가능한 성공 결과로 확정하지 않는다.
        if (result.isPresent() && policy.canStart(clock.instant())) {
          executions.finish(job.id(), "SUCCEEDED", result.get().getId(), clock.instant());
          return;
        }
        if (!accounting.transientFailure) break;
      }
      executions.finish(job.id(), "FAILED", null, clock.instant());
    } catch (RuntimeException failure) {
      executions.finish(job.id(), "FAILED", null, clock.instant());
      log.atWarn()
          .addKeyValue("event", "email.analysis.job.failed")
          .addKeyValue("jobId", job.id())
          .addKeyValue("errorType", failure.getClass().getSimpleName())
          .log("자동 이메일 종목 분석 중단");
    }
  }

  private class CallAccounting implements PortfolioAnalysisCall.Observer {
    private final Job job;
    private UUID callId;
    private boolean transientFailure;

    private CallAccounting(Job job) {
      this.job = job;
    }

    @Override
    public Map<String, Object> prepare(Map<String, Object> request) {
      var limited = new LinkedHashMap<>(request);
      limited.put("max_output_tokens", policy.maxOutputTokens());
      limited.put("max_tool_calls", policy.maxToolCalls());
      return limited;
    }

    @Override
    public void before(String phase, String model) {
      callId = executions.reserve(job, phase, model, clock.instant(), policy);
      // 실행권을 기다리는 사이 마감이 지났을 수도 있으므로 HTTP 전 다시 확인한다.
      if (!policy.canStart(clock.instant())) {
        executions.completeCall(
            callId, "FAILED", 0L, 0L, 0L, 0L, BigDecimal.ZERO, null, clock.instant());
        throw new IllegalStateException("자동 분석 요청 마감 시각을 지났습니다.");
      }
    }

    @Override
    public void after(String response, RuntimeException failure) {
      Integer http =
          failure instanceof HttpStatusCodeException status ? status.getStatusCode().value() : null;
      transientFailure =
          failure instanceof ResourceAccessException
              || (http != null && (http == 429 || http >= 500));
      JsonNode root = null;
      try {
        if (response != null) root = MAPPER.readTree(response);
      } catch (java.io.IOException invalid) {
        // 사용량이 불명확한 응답에서는 예약액을 유지한다.
      }
      Long input = count(root, "usage", "input_tokens");
      Long cached = count(root, "usage", "input_tokens_details", "cached_tokens");
      Long output = count(root, "usage", "output_tokens");
      Long searches = root != null && root.path("output").isArray() ? 0L : null;
      if (searches != null) {
        for (JsonNode item : root.path("output"))
          if ("web_search_call".equals(item.path("type").asText())) searches++;
      }
      BigDecimal cost =
          input != null && output != null && searches != null && (cached == null || cached <= input)
              ? policy
                  .cost(input, cached == null ? 0 : cached, output, searches)
                  .setScale(10, RoundingMode.CEILING)
              : null;
      executions.completeCall(
          callId,
          cost == null ? "UNKNOWN" : failure != null ? "FAILED" : "COMPLETED",
          input,
          cached,
          output,
          searches,
          cost,
          http,
          clock.instant());
      if (failure == null && (cost == null || cost.compareTo(policy.reserveUsd()) > 0))
        throw new IllegalStateException("사용량 미확인 또는 예약 비용 초과로 자동 분석을 중단합니다.");
    }
  }

  private static Long count(JsonNode root, String... path) {
    if (root == null) return null;
    JsonNode node = root;
    for (String name : path) node = node.path(name);
    return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0
        ? node.longValue()
        : null;
  }
}
