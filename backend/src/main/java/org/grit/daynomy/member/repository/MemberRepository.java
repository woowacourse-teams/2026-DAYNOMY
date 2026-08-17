package org.grit.daynomy.member.repository;

import java.util.Optional;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.domain.MemberStatus;
import org.grit.daynomy.member.domain.OAuthProvider;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

  Optional<Member> findByProviderAndProviderId(
      OAuthProvider provider, String providerId); // Google 로그인 시 기존 회원 조회

  boolean existsByProviderAndProviderId(
      OAuthProvider provider, String providerId); // Google 계정 중복 확인

  boolean existsByNickname(String nickname); // 닉네임 중복을 금지할 경우 사용

  boolean existsByIdAndStatus(Long id, MemberStatus status);
}
