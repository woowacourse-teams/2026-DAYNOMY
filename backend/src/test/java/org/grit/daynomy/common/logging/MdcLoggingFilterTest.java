package org.grit.daynomy.common.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class MdcLoggingFilterTest {

  private final MdcLoggingFilter filter = new MdcLoggingFilter();

  @AfterEach
  void clearMdc() {
    MDC.clear();
  }

  @Test
  @DisplayName("HTTP 요청 처리 중 MDC에 requestId와 traceId를 저장하고 완료 후 제거한다")
  void storesAndClearsRequestContext() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/news");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> requestId = new AtomicReference<>();
    AtomicReference<String> traceId = new AtomicReference<>();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          requestId.set(MDC.get("requestId"));
          traceId.set(MDC.get("traceId"));
        });

    assertThat(requestId).hasValueSatisfying(value -> assertThat(value).isNotBlank());
    assertThat(traceId).hasValueSatisfying(value -> assertThat(value).isNotBlank());
    assertThat(MDC.get("requestId")).isNull();
    assertThat(MDC.get("traceId")).isNull();
  }
}
