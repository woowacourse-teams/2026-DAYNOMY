package org.grit.daynomy.emailsubscription.service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;
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
    var current = analyses.get(memberId);
    if (current.analysisId() == null
        || current.analysis() == null
        || current.status() == SavedPortfolioAnalysisResponse.Status.PROCESSING
        || (current.lastSucceededAt() != null && !current.lastSucceededAt().isBefore(cutoff)))
      return;
    var positions = holdings.findAllByPortfolioIdOrderById(portfolio.getId());
    var signalsByAsset =
        new LinkedHashMap<>(
            runs.earningsSignals(
                positions.stream().map(item -> item.getAsset().getId()).toList(),
                cutoff.minus(Duration.ofDays(1)),
                cutoff));
    if (selected != null)
      for (var stock : selected.stocks()) {
        var signals =
            new java.util.ArrayList<>(signalsByAsset.getOrDefault(stock.assetId(), List.of()));
        // 종목과 기준일이 같으면 다음 날에도 같은 가격 신호로 취급한다.
        signals.add("price:" + stock.assetId() + ":" + selected.baseDate());
        signalsByAsset.put(stock.assetId(), signals);
      }
    // 기존에 분석한 종목만 갱신한다. 분석 범위를 임의로 늘리지 않는다.
    var analyzedNames =
        current.analysis().impacts().stream()
            .map(item -> item.assetName())
            .collect(Collectors.toSet());
    var signalsByName =
        positions.stream()
            .filter(item -> analyzedNames.contains(item.getAsset().getName()))
            .filter(item -> signalsByAsset.containsKey(item.getAsset().getId()))
            .collect(
                Collectors.toMap(
                    item -> item.getAsset().getName(),
                    item -> signalsByAsset.get(item.getAsset().getId())));
    var signals =
        signalsByName.values().stream().flatMap(List::stream).distinct().sorted().toList();
    if (signals.isEmpty()) return;
    var claim = runs.claim(memberId, date, signals, dailyLimit, monthlyLimit, now).orElse(null);
    if (claim == null) return;
    var names =
        signalsByName.entrySet().stream()
            .filter(entry -> entry.getValue().stream().anyMatch(claim.signals()::contains))
            .map(entry -> entry.getKey())
            .collect(Collectors.toSet());
    try {
      // 기존 잠금·원문 검증·저장은 재사용하고, 새 신호가 있는 종목만 AI에 전달한다.
      if (!inWindow() || !subscriptions.isEligible(memberId)) {
        runs.complete(claim.id(), null, false, clock.instant());
        return;
      }
      var result = analyses.refreshAutomatically(memberId, names);
      runs.complete(
          claim.id(),
          result.analysisId(),
          result.status() != SavedPortfolioAnalysisResponse.Status.PROCESSING
              && "READY".equals(result.summaryStatus()),
          clock.instant());
    } catch (RuntimeException failure) {
      runs.complete(claim.id(), null, false, clock.instant());
      throw failure;
    }
  }

  private boolean inWindow() {
    var time = clock.instant().atZone(EmailDelivery.ZONE).toLocalTime();
    return !time.isBefore(LocalTime.of(7, 0)) && time.isBefore(LocalTime.of(8, 45));
  }
}
