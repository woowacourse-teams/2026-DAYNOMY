package org.grit.daynomy.portfolio.support;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicLong;
import org.hibernate.resource.jdbc.spi.StatementInspector;

/** Counts only actual member locking statements in the isolated acceptance test context. */
public class MemberLockStatementInspector implements StatementInspector {
  private static final AtomicLong CALLS = new AtomicLong();

  public static long count() {
    return CALLS.get();
  }

  @Override
  public String inspect(String sql) {
    String query = sql.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    if (query.contains(" from members ")
        && (query.contains("for update") || query.contains("for no key update"))) {
      CALLS.incrementAndGet();
    }
    return sql;
  }
}
