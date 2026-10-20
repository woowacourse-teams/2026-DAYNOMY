package org.grit.daynomy.common.logging;

import java.util.IdentityHashMap;
import java.util.Map;

/** 예외 메시지에 포함될 수 있는 SQL 값·인증정보 대신 타입과 호출 위치만 기록한다. */
public final class SanitizedLogException extends RuntimeException {
  private SanitizedLogException(Throwable original) {
    super(original.getClass().getName());
    setStackTrace(original.getStackTrace());
  }

  public static SanitizedLogException from(Throwable original) {
    return copy(original, new IdentityHashMap<>());
  }

  private static SanitizedLogException copy(
      Throwable original, Map<Throwable, SanitizedLogException> seen) {
    var sanitized = new SanitizedLogException(original);
    seen.put(original, sanitized);
    if (original.getCause() != null && !seen.containsKey(original.getCause())) {
      sanitized.initCause(copy(original.getCause(), seen));
    }
    for (Throwable suppressed : original.getSuppressed()) {
      if (!seen.containsKey(suppressed)) sanitized.addSuppressed(copy(suppressed, seen));
    }
    return sanitized;
  }
}
