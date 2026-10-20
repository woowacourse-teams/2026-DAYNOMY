package org.grit.daynomy.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.util.Map;
import java.util.stream.Collectors;
import org.grit.daynomy.common.logging.RequestLoggingFilter;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;
  private final Logger handlerLogger =
      (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
  private final Logger requestLogger = (Logger) LoggerFactory.getLogger(RequestLoggingFilter.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void setUp() {
    appender.start();
    handlerLogger.addAppender(appender);
    requestLogger.addAppender(appender);
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .addFilters(new RequestLoggingFilter())
            .build();
  }

  @AfterEach
  void tearDown() {
    handlerLogger.detachAppender(appender);
    requestLogger.detachAppender(appender);
    appender.stop();
    MDC.clear();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PATCH", "DELETE"})
  void logsUnexpectedTransactionFailureOnceWithCorrelatedSafeStackTrace(String method)
      throws Exception {
    String traceId = "0123456789abcdef0123456789abcdef";
    String spanId = "0123456789abcdef";
    MDC.put("traceId", traceId);
    MDC.put("spanId", spanId);
    mockMvc
        .perform(
            request(HttpMethod.valueOf(method), URI.create("/test/transactions"))
                .header("Authorization", "private-auth")
                .queryParam("secret", "private-query")
                .content("private-body"))
        .andExpect(status().isInternalServerError());
    var failures = appender.list.stream().filter(event -> event.getLevel() == Level.ERROR).toList();
    assertThat(failures).hasSize(1);
    var failure = failures.getFirst();
    assertThat(fields(failure))
        .containsEntry("event", "http.request.failed")
        .containsEntry("method", method)
        .containsEntry("errorCode", "INTERNAL_SERVER_ERROR")
        .containsEntry("errorType", "IllegalStateException");
    var completion = appender.list.getLast();
    assertThat(fields(completion))
        .containsEntry("httpStatus", 500)
        .containsEntry("errorCode", "INTERNAL_SERVER_ERROR");
    assertThat(failure.getMDCPropertyMap().get("requestId"))
        .isNotBlank()
        .isEqualTo(completion.getMDCPropertyMap().get("requestId"));
    assertThat(failure.getMDCPropertyMap().get("traceId"))
        .isEqualTo(traceId)
        .isEqualTo(completion.getMDCPropertyMap().get("traceId"));
    assertThat(failure.getMDCPropertyMap().get("spanId"))
        .isEqualTo(spanId)
        .isEqualTo(completion.getMDCPropertyMap().get("spanId"));
    assertThat(MDC.get("traceId")).isEqualTo(traceId);
    assertThat(MDC.get("spanId")).isEqualTo(spanId);
    String stack = ThrowableProxyUtil.asString(failure.getThrowableProxy());
    assertThat(stack)
        .contains("IllegalStateException", "IllegalArgumentException", "transactions")
        .doesNotContain("private-");
    assertThat(appender.list.toString()).doesNotContain("private-");
  }

  @Test
  void logsBusinessErrorCodeInCompletionWithoutDuplicateWarningOrError() throws Exception {
    mockMvc.perform(post("/test/invalid-transaction")).andExpect(status().isBadRequest());
    assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
    assertThat(fields(appender.list.getLast()))
        .containsEntry("errorCode", "INSUFFICIENT_PORTFOLIO_QUANTITY")
        .containsEntry("httpStatus", 400);
  }

  private Map<String, Object> fields(ILoggingEvent event) {
    return event.getKeyValuePairs().stream()
        .collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
  }

  @Test
  void returnsFieldErrorsWhenRequestBodyValidationFails() throws Exception {
    mockMvc
        .perform(
            post("/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"count\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist())
        .andExpect(jsonPath("$.errors[1].rejectedValue").doesNotExist());
    assertThat(fields(appender.list.getLast())).containsEntry("errorCode", "INVALID_REQUEST");
    assertThat(appender.list).noneMatch(event -> event.getLevel().isGreaterOrEqual(Level.WARN));
  }

  @RestController
  private static class TestController {

    @PostMapping("/test")
    void test(@Valid @RequestBody TestRequest request) {}

    @RequestMapping("/test/transactions")
    void transactions() {
      throw new IllegalStateException(
          "private-sql-and-trade", new IllegalArgumentException("private-cause"));
    }

    @PostMapping("/test/invalid-transaction")
    void invalidTransaction() {
      throw new BusinessException(PortfolioErrorCode.INSUFFICIENT_PORTFOLIO_QUANTITY);
    }
  }

  private record TestRequest(
      @NotBlank(message = "이름을 입력해주세요.") String name,
      @Min(value = 1, message = "개수는 1 이상이어야 합니다.") int count) {}
}
