package org.grit.daynomy.portfolio.repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Function;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
public class GuestPortfolioAnalysisTrialRepository {

  private final JdbcTemplate jdbc;
  private final TransactionTemplate transactions;

  public GuestPortfolioAnalysisTrialRepository(
      JdbcTemplate jdbc, PlatformTransactionManager transactionManager) {
    this.jdbc = jdbc;
    this.transactions = new TransactionTemplate(transactionManager);
  }

  /** Keep the lock only for a state transition; never hold it during an AI request. */
  public <T> T update(String sessionId, Function<String, Update<T>> transition) {
    String key = hash(sessionId);
    return transactions.execute(
        status -> {
          jdbc.update(
              """
              INSERT INTO guest_portfolio_analysis_trials(session_hash, document, expires_at)
              VALUES (?, 'null'::jsonb, now() + interval '1 day')
              ON CONFLICT (session_hash) DO NOTHING
              """,
              key);
          String document =
              jdbc.queryForObject(
                  """
                  SELECT document::text FROM guest_portfolio_analysis_trials
                  WHERE session_hash = ? FOR UPDATE
                  """,
                  String.class,
                  key);
          Update<T> update = transition.apply(document);
          jdbc.update(
              """
              UPDATE guest_portfolio_analysis_trials
              SET document = ?::jsonb, expires_at = now() + interval '1 day'
              WHERE session_hash = ?
              """,
              update.document(),
              key);
          return update.result();
        });
  }

  @Scheduled(cron = "0 17 * * * *", zone = "Asia/Seoul")
  public void deleteExpired() {
    jdbc.update("DELETE FROM guest_portfolio_analysis_trials WHERE expires_at < now()");
  }

  private static String hash(String sessionId) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(sessionId.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
  }

  public record Update<T>(String document, T result) {}
}
