package org.grit.daynomy.member.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberStatus;
import org.grit.daynomy.member.domain.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MemberRepository extends JpaRepository<Member, Long> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<Member> findByProviderAndProviderId(
      OAuthProvider provider, String providerId); // Google 로그인 시 기존 회원 조회

  boolean existsByNickname(String nickname);

  @Modifying
  @Query(
      value =
          """
          INSERT INTO members(provider, provider_id, email, google_name, nickname, profile_image_url,
                              role, status, created_at, updated_at)
          VALUES ('GOOGLE', :providerId, :email, :googleName, :nickname, :profileImageUrl,
                  'USER', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
          ON CONFLICT DO NOTHING
          """,
      nativeQuery = true)
  int insertGoogleMemberIfAbsent(
      @Param("providerId") String providerId,
      @Param("email") String email,
      @Param("googleName") String googleName,
      @Param("nickname") String nickname,
      @Param("profileImageUrl") String profileImageUrl);

  boolean existsByIdAndStatus(Long id, MemberStatus status);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select member from Member member where member.id = :memberId")
  Optional<Member> findByIdForUpdate(@Param("memberId") Long memberId);
}
