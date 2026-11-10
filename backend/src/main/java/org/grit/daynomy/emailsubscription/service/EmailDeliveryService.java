package org.grit.daynomy.emailsubscription.service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.grit.daynomy.emailsubscription.domain.EmailSubscription;
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
  @org.springframework.beans.factory.annotation.Value("${FRONTEND_BASE_URL:http://localhost:5173}")
  private String frontendBaseUrl = "http://localhost:5173";

  private final MemberRepository members;
  private final EmailSubscriptionRepository subscriptions;
  private final EmailDeliveryRepository deliveries;
  private final EmailDigestService digests;
  private final EmailUnsubscribeService unsubscribe;

  public record Attempt(
      UUID deliveryId,
      String recipient,
      String subject,
      String body,
      String htmlBody,
      String unsubscribeUrl) {}

  /** 오전 9시 기준의 저장된 데이터로만 준비한다. 외부 검색·AI 호출은 하지 않는다. */
  public Optional<UUID> prepare(Long memberId, Instant now) {
    var date = now.atZone(EmailDelivery.ZONE).toLocalDate();
    var asOf = date.atTime(9, 0).atZone(EmailDelivery.ZONE).toInstant();
    if (now.isBefore(asOf)
        || !now.atZone(EmailDelivery.ZONE).toLocalTime().isBefore(LocalTime.of(10, 0)))
      return Optional.empty();
    var member = members.findByIdForUpdate(memberId).orElse(null);
    if (member == null || member.isWithdrawn()) return Optional.empty();
    var subscription = subscribed(memberId);
    if (subscription == null) return Optional.empty();
    var saved = deliveries.findByMemberIdAndDeliveryDate(memberId, date);
    if (saved.isPresent()) return Optional.of(saved.get().getId());
    var prepared = digests.compose(memberId, asOf);
    if (prepared.isEmpty()) return Optional.empty();
    var delivery =
        new EmailDelivery(
            member, asOf, prepared.get().stockDigest(), prepared.get().supplement(), date);
    delivery.attachUnsubscribeLink(unsubscribe.link(memberId, subscription.getSubscribedAt()));
    delivery.completeTemplate(member.getNickname(), frontendBaseUrl);
    return Optional.of(deliveries.save(delivery).getId());
  }

  public Optional<Attempt> claim(UUID id, Instant now) {
    var delivery = require(id);
    var member = members.findByIdForUpdate(delivery.getMember().getId()).orElse(null);
    var subscription = member == null || member.isWithdrawn() ? null : subscribed(member.getId());
    if (subscription == null) {
      delivery.cancel();
      return Optional.empty();
    }
    if (!unsubscribe.matches(
        delivery.getUnsubscribeUrl(), member.getId(), subscription.getSubscribedAt())) {
      delivery.cancel();
      return Optional.empty();
    }
    // 과거 AI 본문은 재전송하지 않는다. 현재 보유·가격·자료의 유효성을 재확인한다.
    if (!delivery.currentNotification()
        || !digests.stillValid(
            member.getId(),
            delivery.getAsOf(),
            delivery.getStockDigest(),
            delivery.getSupplementDigest())) {
      delivery.cancel();
      return Optional.empty();
    }
    if (!delivery.claim(now)) return Optional.empty();
    return Optional.of(
        new Attempt(
            id,
            member.getEmail(),
            delivery.getSubject(),
            delivery.getBody()
                + (delivery.getBody().contains(delivery.getUnsubscribeUrl())
                    ? ""
                    : "\n\n수신 해지: " + delivery.getUnsubscribeUrl()),
            delivery.getHtmlBody(),
            delivery.getUnsubscribeUrl()));
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

  private EmailSubscription subscribed(Long memberId) {
    return subscriptions
        .findById(memberId)
        .filter(subscription -> subscription.getStatus() == EmailSubscriptionStatus.SUBSCRIBED)
        .orElse(null);
  }
}
