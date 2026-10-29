package org.grit.daynomy.emailsubscription.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.grit.daynomy.emailsubscription.repository.EmailAutoAnalysisRepository;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.grit.daynomy.portfolio.service.SavedPortfolioAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@ConditionalOnProperty(
    name = {"email.delivery.enabled", "email.auto-analysis.enabled"},
    havingValue = "true")
public class EmailAutoAnalysisService {
  private final EmailSubscriptionRepository subscriptions;
  private final ImportantStockSelectionService selection;
  private final PortfolioRepository portfolios;
  private final PortfolioHoldingRepository holdings;
  private final SavedPortfolioAnalysisService analyses;
  private final EmailAutoAnalysisRepository runs;
  private final int dailyLimit;
  private final int monthlyLimit;
  private final Clock clock;

  @Autowired
  public EmailAutoAnalysisService(
      EmailSubscriptionRepository subscriptions,
      ImportantStockSelectionService selection,
      PortfolioRepository portfolios,
      PortfolioHoldingRepository holdings,
      SavedPortfolioAnalysisService analyses,
      EmailAutoAnalysisRepository runs,
      @Value("${email.auto-analysis.max-daily-runs:0}") int dailyLimit,
      @Value("${email.auto-analysis.max-monthly-runs:0}") int monthlyLimit) {
    this(
        subscriptions,
        selection,
        portfolios,
        holdings,
        analyses,
        runs,
        dailyLimit,
        monthlyLimit,
        Clock.systemUTC());
  }

  EmailAutoAnalysisService(
      EmailSubscriptionRepository subscriptions,
      ImportantStockSelectionService selection,
      PortfolioRepository portfolios,
      PortfolioHoldingRepository holdings,
      SavedPortfolioAnalysisService analyses,
      EmailAutoAnalysisRepository runs,
      int dailyLimit,
      int monthlyLimit,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.selection = selection;
    this.portfolios = portfolios;
    this.holdings = holdings;
    this.analyses = analyses;
    this.runs = runs;
    this.dailyLimit = dailyLimit;
    this.monthlyLimit = monthlyLimit;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 7 * * *", zone = "Asia/Seoul")
  public void runDaily() {
    if (dailyLimit <= 0 || monthlyLimit <= 0 || !inWindow()) return;
    long after = 0;
    while (inWindow()) {
      var members = subscriptions.findEligibleMemberIds(after, PageRequest.of(0, 100));
      if (members.isEmpty()) return;
      for (var memberId : members) {
        if (!inWindow()) return;
        try {
          analyze(memberId);
        } catch (RuntimeException failure) {
          log.atWarn()
              .addKeyValue("errorType", failure.getClass().getSimpleName())
              .log("예약 포트폴리오 분석 실패");
        }
      }
      after = members.getLast();
    }
  }

  private void analyze(Long memberId) {
    var now = clock.instant();
    var date = now.atZone(EmailDelivery.ZONE).toLocalDate();
    var cutoff = date.atTime(7, 0).atZone(EmailDelivery.ZONE).toInstant();
    if (!subscriptions.isEligible(memberId)) return;
    var selected = selection.select(memberId, date).orElse(null);
    var portfolio = portfolios.findByMemberId(memberId).orElse(null);
    if (portfolio == null) return;
    var positions = holdings.findAllByPortfolioIdOrderById(portfolio.getId());
    var signals =
        new ArrayList<>(
            runs.earningsSignals(
                positions.stream().map(item -> item.getAsset().getId()).toList(),
                cutoff.minus(Duration.ofDays(1)),
                cutoff));
    if (selected != null && !selected.stocks().isEmpty())
      // 가격 날짜가 같으면 다음 날에도 같은 신호로 취급한다.
      signals.add("price:" + selected.baseDate());
    if (signals.isEmpty()) return;
    var current = analyses.get(memberId);
    if (current.analysisId() == null
        || current.status() == SavedPortfolioAnalysisResponse.Status.PROCESSING
        || (current.lastSucceededAt() != null && !current.lastSucceededAt().isBefore(cutoff)))
      return;
    var id = runs.claim(memberId, date, signals, dailyLimit, monthlyLimit, now).orElse(null);
    if (id == null) return;
    try {
      // 수동 작업과 동일한 잠금, 최대 5종목 선별, 원문 검증 및 종합 설명 저장을 재사용한다.
      if (!inWindow() || !subscriptions.isEligible(memberId)) {
        runs.complete(id, null, false, clock.instant());
        return;
      }
      var result = analyses.refreshAutomatically(memberId);
      runs.complete(
          id,
          result.analysisId(),
          result.status() != SavedPortfolioAnalysisResponse.Status.PROCESSING
              && "READY".equals(result.summaryStatus()),
          clock.instant());
    } catch (RuntimeException failure) {
      runs.complete(id, null, false, clock.instant());
      throw failure;
    }
  }

  private boolean inWindow() {
    var time = clock.instant().atZone(EmailDelivery.ZONE).toLocalTime();
    return !time.isBefore(LocalTime.of(7, 0)) && time.isBefore(LocalTime.of(8, 45));
  }
}
