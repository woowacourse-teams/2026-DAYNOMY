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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
    name = "members",
    uniqueConstraints = {
      @UniqueConstraint(
          name = "uk_members_provider_provider_id",
          columnNames = {"provider", "provider_id"})
    })
public class Member {

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

  @CreationTimestamp
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @UpdateTimestamp
  @Column(name = "updated_at", nullable = false)
  private LocalDateTime updatedAt;

  @Column(name = "withdrawn_at")
  private LocalDateTime withdrawnAt;

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
    this.withdrawnAt = LocalDateTime.now();
  }

  public boolean isWithdrawn() {
    return status == MemberStatus.WITHDRAWN;
  }
}
