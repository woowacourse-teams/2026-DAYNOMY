package org.grit.daynomy.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "revoked_auth_sessions")
public class RevokedAuthSession {
  @Id
  @Column(name = "session_id", nullable = false)
  private UUID sessionId;

  @Column(name = "revoked_at", nullable = false, insertable = false, updatable = false)
  private Instant revokedAt;
}
