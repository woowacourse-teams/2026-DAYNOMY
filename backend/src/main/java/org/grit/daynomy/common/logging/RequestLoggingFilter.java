package org.grit.daynomy.common.logging;

import jakarta.servlet.AsyncEvent;
import jakarta.servlet.AsyncListener;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Locale;
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
@Order(Ordered.HIGHEST_PRECEDENCE + 2)
public class RequestLoggingFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID = "requestId";
  private static final String AUTOMATION = "automation";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    long startedAt = System.nanoTime();

    MDC.put(REQUEST_ID, UUID.randomUUID().toString());
    MDC.put(AUTOMATION, detectAutomation(request).code());

    log.atDebug()
        .addKeyValue("event", LogEvent.HTTP_REQUEST_STARTED.code())
        .addKeyValue("method", request.getMethod())
        .addKeyValue("uri", request.getRequestURI())
        .log(LogEvent.HTTP_REQUEST_STARTED.message());

    try {
      filterChain.doFilter(request, response);
    } finally {
      try {
        if (request.isAsyncStarted()) {
          var context = MDC.getCopyOfContextMap();
          request
              .getAsyncContext()
              .addListener(
                  new AsyncListener() {
                    @Override
                    public void onComplete(AsyncEvent event) {
                      var previous = MDC.getCopyOfContextMap();
                      try {
                        if (context != null) MDC.setContextMap(context);
                        logCompletion(request, response, startedAt);
                      } finally {
                        if (previous == null) MDC.clear();
                        else MDC.setContextMap(previous);
                      }
                    }

                    @Override
                    public void onStartAsync(AsyncEvent event) {
                      event.getAsyncContext().addListener(this);
                    }

                    @Override
                    public void onTimeout(AsyncEvent event) {}

                    @Override
                    public void onError(AsyncEvent event) {}
                  });
        } else {
          logCompletion(request, response, startedAt);
        }
      } finally {
        MDC.remove(REQUEST_ID);
        MDC.remove(AUTOMATION);
      }
    }
  }

  private void logCompletion(
      HttpServletRequest request, HttpServletResponse response, long startedAt) {
    log.atInfo()
        .addKeyValue("event", LogEvent.HTTP_REQUEST_COMPLETED.code())
        .addKeyValue("method", request.getMethod())
        .addKeyValue("uri", request.getRequestURI())
        .addKeyValue("httpStatus", response.getStatus())
        .addKeyValue("durationMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
        .log(LogEvent.HTTP_REQUEST_COMPLETED.message());
  }

  private AutomationType detectAutomation(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    if (userAgent == null) {
      return AutomationType.UNKNOWN;
    }

    String normalizedUserAgent = userAgent.toLowerCase(Locale.ROOT);
    if (normalizedUserAgent.matches(".*(bot|crawler|spider|slurp|curl|wget|postman).*")) {
      return AutomationType.SUSPECTED_BOT;
    }
    return AutomationType.UNKNOWN;
  }
}
