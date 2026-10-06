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
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.exception.MemberErrorCode;

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

  @Column(name = "google_name", length = 255)
  private String googleName;

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
    this.nickname = normalizeNickname(nickname);
  }

  public void updateGoogleName(String name) {
    if (name != null && !name.isBlank()) {
      String normalized = name.strip();
      this.googleName = normalized.substring(0, Math.min(normalized.length(), 255));
    }
  }

  public static String normalizeNickname(String nickname) {
    String normalized = nickname == null ? "" : nickname.strip();
    if (normalized.isEmpty() || normalized.length() > 20) {
      throw new BusinessException(MemberErrorCode.INVALID_NICKNAME);
    }
    return normalized;
  }

  public void withdraw() {
    this.status = MemberStatus.WITHDRAWN;
    this.withdrawnAt = Instant.now();
  }

  public boolean isWithdrawn() {
    return status == MemberStatus.WITHDRAWN;
  }
}
