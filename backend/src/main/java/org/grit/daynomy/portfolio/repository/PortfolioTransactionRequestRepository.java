package org.grit.daynomy.portfolio.repository;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PortfolioTransactionRequestRepository {
  private final NamedParameterJdbcTemplate jdbc;

  public Optional<StoredRequest> find(Long memberId, String key) {
    return jdbc
        .query(
            """
        SELECT request_hash, response_version, response_body
        FROM portfolio_transaction_requests WHERE member_id = :member AND request_key = :key
        """,
            Map.of("member", memberId, "key", key),
            (row, index) ->
                new StoredRequest(
                    row.getString("request_hash"),
                    row.getInt("response_version"),
                    row.getString("response_body")))
        .stream()
        .findFirst();
  }

  public void limitLockWait() {
    jdbc.getJdbcTemplate().execute("SET LOCAL lock_timeout = '5s'");
  }

  public void save(Long memberId, String key, String hash, String response) {
    jdbc.update(
        """
        INSERT INTO portfolio_transaction_requests(member_id, request_key, request_hash, response_body)
        VALUES (:member, :key, :hash, :response)
        """,
        Map.of("member", memberId, "key", key, "hash", hash, "response", response));
  }

  public record StoredRequest(String hash, int version, String response) {}
}
