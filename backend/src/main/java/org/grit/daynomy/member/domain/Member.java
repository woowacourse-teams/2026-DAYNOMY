package org.grit.daynomy.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.grit.daynomy.common.BaseEntity;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "members",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_members_provider_provider_id",
          columnNames = {"provider", "provider_id"}),
      @UniqueConstraint(name = "uk_members_nickname", columnNames = "nickname")
    })
public class Member extends BaseEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider", nullable = false, length = 20)
  private OAuthProvider provider;

  @Column(name = "provider_id", nullable = false, length = 255)
  private String providerId;

  @Column(name = "email", nullable = false, length = 255)
  private String email;

  @Column(name = "nickname", nullable = false, length = 20)
  private String nickname;

  @Column(name = "profile_image_url", length = 500)
  private String profileImageUrl;

  @Enumerated(EnumType.STRING)
  @Column(name = "role", nullable = false, length = 20)
  private MemberRole role;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private MemberStatus status;

  @Column(name = "withdrawn_at")
  private Instant withdrawnAt;

  private Member(
      OAuthProvider provider,
      String providerId,
      String email,
      String nickname,
      String profileImageUrl) {
    this.provider = provider;
    this.providerId = providerId;
    this.email = email;
    this.nickname = nickname;
    this.profileImageUrl = profileImageUrl;
    this.role = MemberRole.USER;
    this.status = MemberStatus.ACTIVE;
  }

  public static Member createGoogleMember(
      String providerId, String email, String nickname, String profileImageUrl) {
    return new Member(OAuthProvider.GOOGLE, providerId, email, nickname, profileImageUrl);
  }

  public void updateNickname(String nickname) {
    this.nickname = nickname;
  }

  public void withdraw() {
    this.status = MemberStatus.WITHDRAWN;
    this.withdrawnAt = Instant.now();
  }

  public boolean isWithdrawn() {
    return status == MemberStatus.WITHDRAWN;
  }
}
