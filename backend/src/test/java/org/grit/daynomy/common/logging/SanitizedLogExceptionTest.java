package org.grit.daynomy.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import org.junit.jupiter.api.Test;

class SanitizedLogExceptionTest {
  @Test
  void preservesTypesAndFramesWithoutMessagesIncludingCyclicAndSuppressedCauses() {
    var cause = new IllegalArgumentException("private-cause");
    var original = new IllegalStateException("private-trade", cause);
    cause.initCause(original);
    original.addSuppressed(new RuntimeException("private-auth"));

    var sanitized = SanitizedLogException.from(original);
    var output = new StringWriter();
    sanitized.printStackTrace(new PrintWriter(output));

    assertThat(output.toString())
        .contains("IllegalArgumentException", "IllegalStateException", "Suppressed:")
        .doesNotContain("private-");
    assertThat(sanitized.getStackTrace()).isEqualTo(original.getStackTrace());
    assertThat(original.getMessage()).isEqualTo("private-trade");
  }
}
