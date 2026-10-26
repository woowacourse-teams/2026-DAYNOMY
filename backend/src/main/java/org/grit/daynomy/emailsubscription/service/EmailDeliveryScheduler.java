package org.grit.daynomy.emailsubscription.service;

import java.time.Clock;
import java.time.LocalTime;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.grit.daynomy.emailsubscription.repository.EmailAnalysisExecutionRepository;
import org.grit.daynomy.emailsubscription.repository.EmailDeliveryRepository;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@ConditionalOnProperty(
    prefix = "email.delivery",
    name = {"enabled", "scheduling-enabled"},
    havingValue = "true")
public class EmailDeliveryScheduler {
  private final EmailSubscriptionRepository subscriptions;
  private final ImportantStockSelectionService selection;
  private final EmailAnalysisExecutionRepository analyses;
  private final EmailDeliveryService deliveries;
  private final EmailDeliveryRepository jobs;
  private final EmailSendingService sending;
  private final Clock clock;

  @Autowired
  public EmailDeliveryScheduler(
      EmailSubscriptionRepository subscriptions,
      ImportantStockSelectionService selection,
      EmailAnalysisExecutionRepository analyses,
      EmailDeliveryService deliveries,
      EmailDeliveryRepository jobs,
      EmailSendingService sending) {
    this(subscriptions, selection, analyses, deliveries, jobs, sending, Clock.systemUTC());
  }

  EmailDeliveryScheduler(
      EmailSubscriptionRepository subscriptions,
      ImportantStockSelectionService selection,
      EmailAnalysisExecutionRepository analyses,
      EmailDeliveryService deliveries,
      EmailDeliveryRepository jobs,
      EmailSendingService sending,
      Clock clock) {
    this.subscriptions = subscriptions;
    this.selection = selection;
    this.analyses = analyses;
    this.deliveries = deliveries;
    this.jobs = jobs;
    this.sending = sending;
    this.clock = clock;
  }

  @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
  public void prepareDaily() {
    if (!inWindow()) return;
    var date = clock.instant().atZone(EmailAnalysisPolicy.ZONE).toLocalDate();
    var asOf = date.atTime(7, 0).atZone(EmailAnalysisPolicy.ZONE).toInstant();
    long after = 0;
    while (inWindow()) {
      var members = subscriptions.findEligibleMemberIds(after, PageRequest.of(0, 500));
      if (members.isEmpty()) return;
      for (Long memberId : members) {
        if (!inWindow()) return;
        try {
          var selected = selection.select(memberId, date);
          if (selected.isEmpty()) continue;
          var assetIds =
              selected.get().stocks().stream().limit(3).map(stock -> stock.assetId()).toList();
          var ids = analyses.findSuccessfulAnalysisIds(date, asOf, assetIds);
          if (!ids.isEmpty()) deliveries.prepare(memberId, asOf, ids, clock.instant());
        } catch (RuntimeException failure) {
          log.atWarn()
              .addKeyValue("memberId", memberId)
              .addKeyValue("errorType", failure.getClass().getSimpleName())
              .log("이메일 발송 준비 실패");
        }
      }
      after = members.getLast();
    }
  }

  /** 전송 시각과 재시도 횟수는 기존 발송 이력에서 판단하며 한 번에 한 건만 처리한다. */
  @Scheduled(fixedDelay = 1000)
  public void sendDue() {
    if (!inWindow()) return;
    var now = clock.instant();
    var ids =
        jobs.findDueIds(
            now.atZone(EmailAnalysisPolicy.ZONE).toLocalDate(),
            now,
            List.of(EmailDelivery.Status.READY, EmailDelivery.Status.RETRY_PENDING),
            PageRequest.of(0, 1));
    for (var id : ids) {
      try {
        sending.send(id);
      } catch (RuntimeException failure) {
        log.atWarn()
            .addKeyValue("deliveryId", id)
            .addKeyValue("errorType", failure.getClass().getSimpleName())
            .log("이메일 발송 결과 기록 확인 필요");
      }
    }
  }

  private boolean inWindow() {
    var time = clock.instant().atZone(EmailAnalysisPolicy.ZONE).toLocalTime();
    return !time.isBefore(LocalTime.of(9, 0)) && time.isBefore(LocalTime.of(10, 0));
  }
}
