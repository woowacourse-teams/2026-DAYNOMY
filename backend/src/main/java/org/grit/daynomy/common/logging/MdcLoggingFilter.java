package org.grit.daynomy.common.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Order(Ordered.HIGHEST_PRECEDENCE)
@Component
public class MdcLoggingFilter extends OncePerRequestFilter {

  private static final String REQUEST_ID = "requestId";
  private static final String TRACE_ID = "traceId";

  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain)
      throws ServletException, IOException {
    MDC.put(REQUEST_ID, UUID.randomUUID().toString());
    MDC.put(TRACE_ID, UUID.randomUUID().toString());

    try {
      filterChain.doFilter(request, response);
    } finally {
      MDC.remove(REQUEST_ID);
      MDC.remove(TRACE_ID);
    }
  }
}
