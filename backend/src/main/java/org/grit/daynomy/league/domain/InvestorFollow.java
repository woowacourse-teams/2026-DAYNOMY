package org.grit.daynomy.league.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.member.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "investor_follows",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_investor_follows_follower_profile",
            columnNames = {"follower_member_id", "profile_id"}))
public class InvestorFollow extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "follower_member_id", nullable = false)
  private Member follower;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "profile_id", nullable = false)
  private InvestorProfile profile;

  public InvestorFollow(Member follower, InvestorProfile profile) {
    this.follower = Objects.requireNonNull(follower);
    this.profile = Objects.requireNonNull(profile);
  }
}
