package org.grit.daynomy.emailsubscription.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.member.domain.Member;

@Getter
@Entity
@Table(name = "email_subscriptions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmailSubscription extends BaseEntity {

  @Id
  @Column(name = "member_id")
  private Long memberId;

  @MapsId
  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private EmailSubscriptionStatus status;

  @Column(name = "subscribed_at")
  private Instant subscribedAt;

  public EmailSubscription(Member member) {
    this.member = Objects.requireNonNull(member);
    this.status = EmailSubscriptionStatus.UNDECIDED;
  }

  public void subscribe(Instant now) {
    if (status != EmailSubscriptionStatus.SUBSCRIBED) {
      subscribedAt = Objects.requireNonNull(now);
      status = EmailSubscriptionStatus.SUBSCRIBED;
    }
  }

  public void dismiss() {
    if (status == EmailSubscriptionStatus.UNDECIDED) {
      status = EmailSubscriptionStatus.DECLINED;
    }
  }

  public void unsubscribe() {
    if (status == EmailSubscriptionStatus.SUBSCRIBED) {
      status = EmailSubscriptionStatus.UNSUBSCRIBED;
    }
  }
}
