package org.grit.daynomy.auth.repository;

import java.util.Optional;
import org.grit.daynomy.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  Optional<RefreshToken> findByTokenHash(String tokenHash);

  void deleteByTokenHash(String tokenHash);

  void deleteAllByMember_Id(Long memberId);
}
