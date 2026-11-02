package org.grit.daynomy.auth.repository;

import java.util.UUID;
import org.grit.daynomy.auth.domain.RevokedAuthSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RevokedAuthSessionRepository extends JpaRepository<RevokedAuthSession, UUID> {
  @Modifying
  @Query(
      value =
          "INSERT INTO revoked_auth_sessions(session_id) VALUES (:sessionId) ON CONFLICT DO NOTHING",
      nativeQuery = true)
  int revoke(@Param("sessionId") UUID sessionId);
}
