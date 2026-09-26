package org.grit.daynomy.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID = "requestId";
  private static final String TRACE_ID = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startedAt = System.nanoTime();

    MDC.put(REQUEST_ID, UUID.randomUUID().toString());
    MDC.put(TRACE_ID, UUID.randomUUID().toString());

    log.atDebug()
        .addKeyValue("event", LogEvent.HTTP_REQUEST_STARTED.code())
        .addKeyValue("method", request.getMethod())
        .addKeyValue("uri", request.getRequestURI())
        .log(LogEvent.HTTP_REQUEST_STARTED.message());

    try {
      filterChain.doFilter(request, response);
    } finally {
      try {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

        log.atInfo()
            .addKeyValue("event", LogEvent.HTTP_REQUEST_COMPLETED.code())
            .addKeyValue("method", request.getMethod())
            .addKeyValue("uri", request.getRequestURI())
            .addKeyValue("httpStatus", response.getStatus())
            .addKeyValue("durationMs", durationMs)
            .log(LogEvent.HTTP_REQUEST_COMPLETED.message());
      } finally {
        MDC.remove(REQUEST_ID);
        MDC.remove(TRACE_ID);
      }
    }
  }
}
