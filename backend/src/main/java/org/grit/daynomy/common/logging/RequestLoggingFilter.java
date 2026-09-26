package org.grit.daynomy.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final String HTTP_REQUEST_STARTED = "http.request.started";
  private static final String HTTP_REQUEST_COMPLETED = "http.request.completed";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startedAt = System.nanoTime();

    log.atDebug()
        .addKeyValue("event", HTTP_REQUEST_STARTED)
        .addKeyValue("method", request.getMethod())
        .addKeyValue("uri", request.getRequestURI())
        .log("HTTP request started");

    try {
      filterChain.doFilter(request, response);
    } finally {
      long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);

      log.atInfo()
          .addKeyValue("event", HTTP_REQUEST_COMPLETED)
          .addKeyValue("method", request.getMethod())
          .addKeyValue("uri", request.getRequestURI())
          .addKeyValue("httpStatus", response.getStatus())
          .addKeyValue("durationMs", durationMs)
          .log("HTTP request completed");
    }
  }
}
