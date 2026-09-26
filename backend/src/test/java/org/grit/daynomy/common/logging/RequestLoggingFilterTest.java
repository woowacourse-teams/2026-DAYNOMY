package org.grit.daynomy.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestLoggingFilterTest {

  private final RequestLoggingFilter requestLoggingFilter = new RequestLoggingFilter();
  private final Logger logger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  private Level originalLevel;

  @BeforeEach
  void setUp() {
    originalLevel = logger.getLevel();
    logger.setLevel(Level.DEBUG);
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    logger.setLevel(originalLevel);
    appender.stop();
    MDC.clear();
  }

  @Test
  void logsRequestStartAndCompletion() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/news");
    MockHttpServletResponse response = new MockHttpServletResponse();
    AtomicReference<String> requestId = new AtomicReference<>();
    AtomicReference<String> traceId = new AtomicReference<>();
    FilterChain filterChain =
        (servletRequest, servletResponse) -> {
          requestId.set(MDC.get("requestId"));
          traceId.set(MDC.get("traceId"));
          assertThat(MDC.get("automation")).isEqualTo("unknown");
          ((MockHttpServletResponse) servletResponse).setStatus(201);
        };

    requestLoggingFilter.doFilter(request, response, filterChain);

    assertThat(requestId).hasValueSatisfying(value -> assertThat(value).isNotBlank());
    assertThat(traceId).hasValueSatisfying(value -> assertThat(value).isNotBlank());
    assertThat(MDC.get("requestId")).isNull();
    assertThat(MDC.get("traceId")).isNull();
    assertThat(MDC.get("automation")).isNull();
    assertThat(appender.list).hasSize(2);

    ILoggingEvent startedLog = appender.list.get(0);
    assertThat(startedLog.getLevel()).isEqualTo(Level.DEBUG);
    assertThat(startedLog.getFormattedMessage()).isEqualTo(LogEvent.HTTP_REQUEST_STARTED.message());
    assertThat(keyValues(startedLog))
        .containsEntry("event", "http.request.started")
        .containsEntry("method", "GET")
        .containsEntry("uri", "/api/news");
    assertThat(startedLog.getMDCPropertyMap()).containsEntry("automation", "unknown");

    ILoggingEvent completedLog = appender.list.get(1);
    assertThat(completedLog.getLevel()).isEqualTo(Level.INFO);
    assertThat(completedLog.getFormattedMessage())
        .isEqualTo(LogEvent.HTTP_REQUEST_COMPLETED.message());
    assertThat(keyValues(completedLog))
        .containsEntry("event", "http.request.completed")
        .containsEntry("method", "GET")
        .containsEntry("uri", "/api/news")
        .containsEntry("httpStatus", 201)
        .containsKey("durationMs");
    assertThat(completedLog.getMDCPropertyMap()).containsEntry("automation", "unknown");
  }

  @Test
  void classifiesAutomationFromUserAgent() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/news");
    request.addHeader("User-Agent", "curl/8.0");
    AtomicReference<String> automation = new AtomicReference<>();
    FilterChain filterChain =
        (servletRequest, servletResponse) -> automation.set(MDC.get("automation"));

    requestLoggingFilter.doFilter(request, new MockHttpServletResponse(), filterChain);

    assertThat(automation).hasValue("suspected_bot");
    assertThat(MDC.get("automation")).isNull();
    assertThat(appender.list)
        .allSatisfy(
            loggingEvent ->
                assertThat(loggingEvent.getMDCPropertyMap())
                    .containsEntry("automation", "suspected_bot"));
  }

  @Test
  void excludesQueryStringFromRequestUri() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/news");
    request.setQueryString("keyword=secret");

    requestLoggingFilter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

    assertThat(appender.list)
        .allSatisfy(
            loggingEvent -> {
              assertThat(keyValues(loggingEvent)).containsEntry("uri", "/api/news");
              assertThat(loggingEvent.getFormattedMessage()).doesNotContain("keyword=secret");
            });
  }

  @Test
  void logsCompletionAndPropagatesException() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/news");
    FilterChain filterChain =
        (servletRequest, servletResponse) -> {
          throw new ServletException("test exception");
        };

    assertThatThrownBy(
            () ->
                requestLoggingFilter.doFilter(request, new MockHttpServletResponse(), filterChain))
        .isInstanceOf(ServletException.class)
        .hasMessage("test exception");

    assertThat(appender.list).hasSize(2);
    assertThat(keyValues(appender.list.get(1)))
        .containsEntry("event", "http.request.completed")
        .containsEntry("method", "POST")
        .containsEntry("uri", "/api/news");
  }

  private Map<String, Object> keyValues(ILoggingEvent loggingEvent) {
    return loggingEvent.getKeyValuePairs().stream()
        .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
  }
}
