package org.grit.daynomy.external.nyse;

import static org.assertj.core.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

class NyseCalendarClientTest {
  private MockWebServer server;
  private NyseCalendarClient client;
  private final Logger logger = (Logger) LoggerFactory.getLogger(NyseCalendarClient.class);
  private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

  @BeforeEach
  void setUp() throws Exception {
    appender.start();
    logger.addAppender(appender);
    server = new MockWebServer();
    server.start();
    client =
        new NyseCalendarClient(
            new NyseCalendarProperties(
                server.url("/").toString(), Duration.ofSeconds(1), Duration.ofSeconds(1)),
            org.springframework.web.client.RestClient.builder());
  }

  @AfterEach
  void tearDown() throws Exception {
    logger.detachAppender(appender);
    appender.stop();
    server.shutdown();
  }

  @Test
  void readsPublishedAnnualClosuresWithoutATokenAndKeepsEarlyClosingsAsTradingDays()
      throws Exception {
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "text/html;charset=UTF-8").setBody(fixture()));

    var calendar = client.annualHolidays();

    assertThat(calendar).containsOnlyKeys(2026, 2027, 2028);
    assertThat(calendar.get(2026))
        .containsExactlyInAnyOrder(
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 1, 19),
            LocalDate.of(2026, 2, 16),
            LocalDate.of(2026, 4, 3),
            LocalDate.of(2026, 5, 25),
            LocalDate.of(2026, 6, 19),
            LocalDate.of(2026, 7, 3),
            LocalDate.of(2026, 9, 7),
            LocalDate.of(2026, 11, 26),
            LocalDate.of(2026, 12, 25));
    assertThat(calendar.get(2026))
        .doesNotContain(LocalDate.of(2026, 11, 27), LocalDate.of(2026, 12, 24));
    assertThat(calendar.get(2027))
        .hasSize(10)
        .contains(LocalDate.of(2027, 6, 18), LocalDate.of(2027, 7, 5), LocalDate.of(2027, 12, 24));
    assertThat(calendar.get(2028)).hasSize(9).doesNotContain(LocalDate.of(2028, 1, 1));
    var request = server.takeRequest();
    assertThat(request.getPath()).isEqualTo("/trade/hours-calendars");
    assertThat(request.getHeader("Authorization")).isNull();
  }

  @Test
  void includesAdditionalOfficialClosureRows() throws Exception {
    String closure =
        """
        <tr><th>Additional market closure</th><td>Tuesday, November 10</td>
        <td>Wednesday, November 10</td><td>Friday, November 10</td></tr>
        """;
    server.enqueue(
        new MockResponse()
            .setHeader("Content-Type", "text/html;charset=UTF-8")
            .setBody(fixture().replace("</tbody>", closure + "</tbody>")));

    assertThat(client.annualHolidays().get(2026)).contains(LocalDate.of(2026, 11, 10)).hasSize(11);
  }

  @ParameterizedTest
  @MethodSource("invalidCalendars")
  void rejectsMissingIncompleteAmbiguousAndMalformedCalendars(String body) {
    server.enqueue(
        new MockResponse().setHeader("Content-Type", "text/html;charset=UTF-8").setBody(body));

    assertThatThrownBy(client::annualHolidays)
        .isInstanceOfSatisfying(
            BusinessException.class,
            exception ->
                assertThat(exception.errorCode())
                    .isEqualTo(ExternalErrorCode.NYSE_CALENDAR_REQUEST_FAILED));
  }

  private static Stream<String> invalidCalendars() throws Exception {
    String page = fixture();
    return Stream.of(
        "",
        "<html>unavailable</html>",
        page + page,
        page.replace("<th>2028</th>", "<th>2027</th>"),
        page.replace("<th>2028</th>", "<th>2028?</th>"),
        page.replace("<td>Friday, January 1</td>", ""),
        page.replace("Friday, January 1", "TBD"),
        page.replace("Monday, January 19", "Tuesday, January 19"),
        page.replace("Monday, January 19", "Monday, February 30"),
        page.replace("Monday, January 19", "Thursday, January 1"),
        page.replace("Thursday, January 1", "—*"),
        page.replace("—*", "—".repeat(2)),
        page.replaceAll("(?s)<tr>\\s*<th>Good Friday</th>.*?</tr>", ""));
  }

  @Test
  void preservesSafeErrorsForHttpFailuresAndTimeouts() {
    server.enqueue(new MockResponse().setResponseCode(503).setBody("private-provider-message"));
    assertThatThrownBy(client::annualHolidays)
        .isInstanceOf(BusinessException.class)
        .hasMessageNotContaining("private-provider-message")
        .hasCause(null);
    assertThat(appender.list)
        .singleElement()
        .satisfies(
            event ->
                assertThat(event.getFormattedMessage()).contains("errorType=ServiceUnavailable"));
    client =
        new NyseCalendarClient(
            new NyseCalendarProperties(
                server.url("/").toString(), Duration.ofMillis(100), Duration.ofMillis(100)),
            org.springframework.web.client.RestClient.builder());
    server.enqueue(new MockResponse().setBody("late").setBodyDelay(1, TimeUnit.SECONDS));
    assertThatThrownBy(client::annualHolidays).isInstanceOf(BusinessException.class);
    assertThat(appender.list)
        .hasSize(2)
        .allSatisfy(
            event -> {
              assertThat(event.getFormattedMessage()).doesNotContain("private-provider-message");
              assertThat(event.getThrowableProxy()).isNull();
            });
    assertThat(appender.list.getLast().getFormattedMessage())
        .contains("errorType=RestClientException");
  }

  @Test
  void logsValidationFailureTypeWithoutTheProviderBody() {
    server.enqueue(new MockResponse().setBody("<html>private-provider-message</html>"));

    assertThatThrownBy(client::annualHolidays).isInstanceOf(BusinessException.class).hasCause(null);

    assertThat(appender.list)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getFormattedMessage())
                  .contains("errorType=IllegalArgumentException")
                  .doesNotContain("private-provider-message");
              assertThat(event.getThrowableProxy()).isNull();
            });
  }

  private static String fixture() throws Exception {
    try (var stream = NyseCalendarClientTest.class.getResourceAsStream("/nyse/holidays.html")) {
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
