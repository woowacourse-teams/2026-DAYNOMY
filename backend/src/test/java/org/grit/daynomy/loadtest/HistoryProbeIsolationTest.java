package org.grit.daynomy.loadtest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

class HistoryProbeIsolationTest {
  @Test
  void disablesSessionCleanupThroughTheActualEnvironmentBinder() {
    var environment = new StandardEnvironment();
    environment
        .getPropertySources()
        .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
    String source = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
    environment
        .getPropertySources()
        .replace(
            source,
            new SystemEnvironmentPropertySource(
                source, Map.of("SPRING_SESSION_JDBC_CLEANUP_CRON", "-")));
    assertThat(
            Binder.get(environment)
                .bind("spring.session.jdbc.cleanup-cron", String.class)
                .orElse(null))
        .isEqualTo("-");
  }
}
