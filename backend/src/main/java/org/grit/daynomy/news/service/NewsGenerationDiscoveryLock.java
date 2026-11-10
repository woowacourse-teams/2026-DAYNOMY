package org.grit.daynomy.news.service;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.news.domain.NewsGenerationType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NewsGenerationDiscoveryLock {

  private final DataSource dataSource;

  public <T> Optional<T> withLock(NewsGenerationType type, Supplier<T> action) {
    // shortcut: 주제 수집 중 연결 1개를 점유한다, 생성 병렬성이 커지면 lease 테이블로 전환한다.
    try (Connection connection = dataSource.getConnection()) {
      long lockKey = type == NewsGenerationType.KOREA ? 260_001L : 260_002L;
      if (!tryLock(connection, lockKey)) {
        return Optional.empty();
      }
      try {
        return Optional.of(action.get());
      } finally {
        unlock(connection, lockKey);
      }
    } catch (SQLException exception) {
      throw new IllegalStateException("News topic discovery lock failed.", exception);
    }
  }

  private boolean tryLock(Connection connection, long lockKey) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT pg_try_advisory_lock(?)")) {
      statement.setLong(1, lockKey);
      try (ResultSet result = statement.executeQuery()) {
        return result.next() && result.getBoolean(1);
      }
    }
  }

  private void unlock(Connection connection, long lockKey) throws SQLException {
    try (PreparedStatement statement =
        connection.prepareStatement("SELECT pg_advisory_unlock(?)")) {
      statement.setLong(1, lockKey);
      statement.execute();
    }
  }
}
