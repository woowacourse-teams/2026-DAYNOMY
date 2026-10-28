package org.grit.daynomy.emailsubscription.service;

import java.time.Instant;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.emailsubscription.domain.EmailAnalysisPolicy;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.grit.daynomy.emailsubscription.domain.EmailStockDigest;
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
  private final EmailStockDigestService digests;
  private final EmailUnsubscribeService unsubscribe;
  private final EmailDigestSupplementService supplements;

  public record Attempt(
      UUID deliveryId,
      String recipient,
      String subject,
      String body,
      String htmlBody,
      String unsubscribeUrl) {}

  /** 호출부는 당일 SUCCEEDED 자동 분석 ID만 전달한다. 외부 메일 전송은 수행하지 않는다. */
  public Optional<UUID> prepare(Long memberId, Instant asOf, List<Long> analysisIds, Instant now) {
    var date = now.atZone(EmailAnalysisPolicy.ZONE).toLocalDate();
    if (!asOf.atZone(EmailAnalysisPolicy.ZONE).toLocalDate().equals(date)
        || !now.atZone(EmailAnalysisPolicy.ZONE).toLocalTime().isBefore(LocalTime.of(10, 0)))
      return Optional.empty();
    var member = members.findByIdForUpdate(memberId).orElse(null);
    if (member == null || member.isWithdrawn()) return Optional.empty();
    var subscription = subscribed(memberId);
    if (subscription == null) return Optional.empty();
    var saved = deliveries.findByMemberIdAndDeliveryDate(memberId, date);
    if (saved.isPresent()) return Optional.of(saved.get().getId());
    var ids = analysisIds.stream().distinct().limit(3).toList();
    var covered =
        ids.isEmpty() ? List.<Long>of() : deliveries.findCoveredAnalysisIds(memberId, ids);
    var unseen = ids.stream().filter(id -> !covered.contains(id)).toList();
    var digest =
        unseen.isEmpty()
            ? null
            : digests
                .compose(memberId, asOf, unseen)
                .map(content -> newInformation(memberId, content))
                .filter(content -> !content.stocks().isEmpty())
                .orElse(null);
    var supplement = supplements.compose(memberId, now);
    if (digest == null && !supplement.shouldSend()) return Optional.empty();
    var delivery = new EmailDelivery(member, asOf, digest, supplement, date);
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
    // 본문 재구성으로 현재 보유·가격·분석 유효성을 확인하고 달라진 내용은 보내지 않는다.
    boolean valid = true;
    if (delivery.getStockDigest() != null) {
      var current = digests.compose(member.getId(), delivery.getAsOf(), delivery.getAnalysisIds());
      valid =
          current.isPresent()
              && current
                  .get()
                  .bodyWithoutChanges()
                  .equals(delivery.getStockDigest().bodyWithoutChanges());
    } else if (!delivery.getAnalysisIds().isEmpty()) valid = false;
    if (valid
        && delivery.getSupplementDigest() != null
        && (delivery.getSupplementDigest().assets() != null
            || !delivery.getSupplementDigest().events().isEmpty()))
      valid = delivery.getSupplementDigest().stillValid(supplements.compose(member.getId(), now));
    if (!valid) {
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

  private EmailStockDigest newInformation(Long memberId, EmailStockDigest digest) {
    var previous = new HashMap<Long, EmailStockDigest>();
    var blocked = new HashSet<Long>();
    for (var stock : digest.stocks()) {
      deliveries
          .findLastNoticeForAsset(memberId, stock.assetId())
          .ifPresent(
              delivery -> {
                if (delivery.getStatus() != EmailDelivery.Status.ACCEPTED
                    || delivery.getStockDigest() == null) blocked.add(stock.assetId());
                else previous.put(stock.assetId(), delivery.getStockDigest());
              });
    }
    return digest.newInformation(previous, blocked);
  }

  private EmailSubscription subscribed(Long memberId) {
    return subscriptions
        .findById(memberId)
        .filter(subscription -> subscription.getStatus() == EmailSubscriptionStatus.SUBSCRIBED)
        .orElse(null);
  }
}
