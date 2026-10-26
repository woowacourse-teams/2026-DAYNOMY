package org.grit.daynomy.league.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;
import org.grit.daynomy.league.domain.LeagueTypes.ExperienceLevel;
import org.grit.daynomy.league.domain.LeagueTypes.RiskProfile;
import org.grit.daynomy.member.domain.Member;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@org.hibernate.annotations.DynamicUpdate
@Table(
    name = "public_investor_profiles",
    uniqueConstraints = {
      @UniqueConstraint(name = "uk_public_investor_profiles_member", columnNames = "member_id"),
      @UniqueConstraint(name = "uk_public_investor_profiles_public_id", columnNames = "public_id"),
      @UniqueConstraint(
          name = "uk_public_investor_profiles_display_name",
          columnNames = "display_name")
    })
public class InvestorProfile extends BaseEntity implements LeagueInvestorValue {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @OneToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "member_id", nullable = false)
  private Member member;

  public Long memberId() {
    return member.getId();
  }

  @Column(name = "public_id", nullable = false, length = 36)
  private String publicId;

  @Column(name = "display_name", nullable = false, length = 20)
  private String displayName;

  @Column(nullable = false, length = 120)
  private String bio;

  @Enumerated(EnumType.STRING)
  @Column(name = "experience_level", nullable = false, length = 20)
  private ExperienceLevel experienceLevel;

  @Enumerated(EnumType.STRING)
  @Column(name = "risk_profile", nullable = false, length = 20)
  private RiskProfile riskProfile;

  @Column(name = "profile_public", nullable = false)
  private boolean profilePublic;

  @Column(name = "league_enabled", nullable = false)
  private boolean leagueEnabled;

  @Column(name = "allocation_public", nullable = false)
  private boolean allocationPublic = true;

  @Column(name = "premium_detail_enabled", nullable = false)
  private boolean detailPublic;

  @Column(name = "league_enabled_at")
  private Instant leagueEnabledAt;

  @Column(name = "tracking_started_on")
  private LocalDate trackingStartedOn;

  @OneToMany(mappedBy = "profile", cascade = CascadeType.ALL)
  private List<LeagueParticipation> participations = new ArrayList<>();

  public void startTracking(LocalDate date) {
    if (trackingStartedOn == null || date.isBefore(trackingStartedOn)) trackingStartedOn = date;
  }

  public void correctTrackingStart() {
    trackingStartedOn = null;
  }

  public LocalDate eligibleFrom() {
    return participations.stream()
        .filter(period -> period.getEndedAt() == null)
        .map(LeagueParticipation::eligibleFrom)
        .findFirst()
        .orElse(null);
  }

  private InvestorProfile(
      Member member,
      String displayName,
      String bio,
      ExperienceLevel experienceLevel,
      RiskProfile riskProfile) {
    this.member = Objects.requireNonNull(member);
    this.publicId = UUID.randomUUID().toString();
    changeProfile(displayName, bio, experienceLevel, riskProfile);
  }

  public static InvestorProfile create(
      Member member,
      String displayName,
      String bio,
      ExperienceLevel experienceLevel,
      RiskProfile riskProfile) {
    return new InvestorProfile(member, displayName, bio, experienceLevel, riskProfile);
  }

  public void changeProfile(
      String displayName, String bio, ExperienceLevel experienceLevel, RiskProfile riskProfile) {
    changeDisplayName(displayName);
    this.bio = bio == null ? "" : bio.trim();
    this.experienceLevel = Objects.requireNonNull(experienceLevel);
    this.riskProfile = Objects.requireNonNull(riskProfile);
  }

  public void changeDisplayName(String displayName) {
    this.displayName = Member.normalizeNickname(displayName);
  }

  public void changePublication(
      boolean profilePublic,
      boolean leagueEnabled,
      boolean allocationPublic,
      boolean detailPublic,
      Instant changedAt) {
    boolean participating = profilePublic && leagueEnabled;
    if (participating && !this.leagueEnabled) {
      if (leagueEnabledAt == null) leagueEnabledAt = changedAt;
      participations.add(new LeagueParticipation(this, changedAt));
    } else if (!participating && this.leagueEnabled) {
      participations.stream()
          .filter(period -> period.getEndedAt() == null)
          .forEach(period -> period.end(changedAt));
    }
    this.profilePublic = profilePublic;
    this.leagueEnabled = profilePublic && leagueEnabled;
    this.allocationPublic = allocationPublic;
    this.detailPublic = profilePublic && detailPublic;
  }
}
