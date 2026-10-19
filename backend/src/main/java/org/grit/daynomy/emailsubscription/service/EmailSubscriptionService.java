package org.grit.daynomy.emailsubscription.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.emailsubscription.domain.EmailSubscription;
import org.grit.daynomy.emailsubscription.dto.EmailSubscriptionResponse;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailSubscriptionService {

  private final MemberRepository members;
  private final EmailSubscriptionRepository subscriptions;

  public EmailSubscriptionResponse get(Long memberId) {
    Member member =
        requireActive(
            members
                .findById(memberId)
                .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)));
    return EmailSubscriptionResponse.from(
        member.getEmail(), subscriptions.findById(memberId).orElse(null));
  }

  @Transactional
  public EmailSubscriptionResponse subscribe(Long memberId) {
    Member member = lockedMember(memberId);
    EmailSubscription subscription = findOrNew(member);
    subscription.subscribe(Instant.now().truncatedTo(ChronoUnit.MICROS));
    subscriptions.save(subscription);
    return EmailSubscriptionResponse.from(member.getEmail(), subscription);
  }

  @Transactional
  public EmailSubscriptionResponse dismiss(Long memberId) {
    Member member = lockedMember(memberId);
    EmailSubscription subscription = findOrNew(member);
    subscription.dismiss();
    subscriptions.save(subscription);
    return EmailSubscriptionResponse.from(member.getEmail(), subscription);
  }

  @Transactional
  public EmailSubscriptionResponse unsubscribe(Long memberId) {
    Member member = lockedMember(memberId);
    EmailSubscription subscription = subscriptions.findById(memberId).orElse(null);
    if (subscription != null) {
      subscription.unsubscribe();
    }
    return EmailSubscriptionResponse.from(member.getEmail(), subscription);
  }

  private EmailSubscription findOrNew(Member member) {
    return subscriptions.findById(member.getId()).orElseGet(() -> new EmailSubscription(member));
  }

  private Member lockedMember(Long memberId) {
    return requireActive(
        members
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND)));
  }

  private Member requireActive(Member member) {
    if (member.isWithdrawn()) {
      throw new BusinessException(MemberErrorCode.WITHDRAWN_MEMBER);
    }
    return member;
  }
}
