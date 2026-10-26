package org.grit.daynomy.emailsubscription.service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus;
import org.grit.daynomy.emailsubscription.repository.EmailDeliveryRepository;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class EmailDeliveryService {
  private final MemberRepository members;
  private final EmailSubscriptionRepository subscriptions;
  private final EmailDeliveryRepository deliveries;
  private final EmailStockDigestService digests;

  public record Attempt(UUID deliveryId, String recipient, String subject, String body) {}

  /** 호출부는 당일 SUCCEEDED 자동 분석 ID만 전달한다. 외부 메일 전송은 수행하지 않는다. */
  public Optional<UUID> prepare(Long memberId, Instant asOf, List<Long> analysisIds, Instant now) {
    var date = now.atZone(EmailAnalysisPolicy.ZONE).toLocalDate();
    if (!asOf.atZone(EmailAnalysisPolicy.ZONE).toLocalDate().equals(date)
        || !now.atZone(EmailAnalysisPolicy.ZONE).toLocalTime().isBefore(LocalTime.of(10, 0)))
      return Optional.empty();
    var member = members.findByIdForUpdate(memberId).orElse(null);
    if (member == null || member.isWithdrawn() || !subscribed(memberId)) return Optional.empty();
    var saved = deliveries.findByMemberIdAndDeliveryDate(memberId, date);
    if (saved.isPresent()) return Optional.of(saved.get().getId());
    var ids = analysisIds.stream().distinct().limit(3).toList();
    if (ids.isEmpty()) return Optional.empty();
    var covered = deliveries.findCoveredAnalysisIds(memberId, ids);
    var unseen = ids.stream().filter(id -> !covered.contains(id)).toList();
    if (unseen.isEmpty()) return Optional.empty();
    return digests
        .compose(memberId, asOf, unseen)
        .map(digest -> deliveries.save(new EmailDelivery(member, asOf, digest, date)).getId());
  }

  public Optional<Attempt> claim(UUID id, Instant now) {
    var delivery = require(id);
    var member = members.findByIdForUpdate(delivery.getMember().getId()).orElse(null);
    if (member == null || member.isWithdrawn() || !subscribed(member.getId())) {
      delivery.cancel();
      return Optional.empty();
    }
    // 본문 재구성으로 현재 보유·가격·분석 유효성을 확인하고 달라진 내용은 보내지 않는다.
    var current = digests.compose(member.getId(), delivery.getAsOf(), delivery.getAnalysisIds());
    if (current.isEmpty() || !current.get().body().equals(delivery.getBody())) {
      delivery.cancel();
      return Optional.empty();
    }
    if (!delivery.claim(now)) return Optional.empty();
    return Optional.of(
        new Attempt(id, member.getEmail(), delivery.getSubject(), delivery.getBody()));
  }

  public void accepted(UUID id, String messageId, Instant acceptedAt) {
    require(id).accepted(messageId, acceptedAt);
  }

  public void failed(UUID id, boolean retryable, Instant now) {
    require(id).failed(retryable, now);
  }

  public void unknown(UUID id) {
    require(id).unknown();
  }

  private EmailDelivery require(UUID id) {
    return deliveries
        .findByIdForUpdate(id)
        .orElseThrow(() -> new IllegalArgumentException("발송 작업이 존재하지 않습니다."));
  }

  private boolean subscribed(Long memberId) {
    return subscriptions
        .findById(memberId)
        .filter(subscription -> subscription.getStatus() == EmailSubscriptionStatus.SUBSCRIBED)
        .isPresent();
  }
}
