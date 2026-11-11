package org.grit.daynomy.external.nyse;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class NyseCalendarClient {
  private static final String PAGE = "/trade/hours-calendars";
  private static final Set<String> REQUIRED_HOLIDAYS =
      Set.of(
          "New Year's Day",
          "Martin Luther King, Jr. Day",
          "Washington's Birthday",
          "Good Friday",
          "Memorial Day",
          "Juneteenth National Independence Day",
          "Independence Day",
          "Labor Day",
          "Thanksgiving Day",
          "Christmas Day");
  private static final Pattern DATE =
      Pattern.compile("^([A-Za-z]+, [A-Za-z]+ \\d{1,2})(?:\\s*\\([^)]*\\))?\\**$");
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("EEEE, MMMM d uuuu", Locale.US)
          .withResolverStyle(ResolverStyle.STRICT);
  private final RestClient client;

  public NyseCalendarClient(NyseCalendarProperties properties, RestClient.Builder builder) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    client =
        builder
            .baseUrl(properties.baseUrl())
            .defaultHeader("User-Agent", "Mozilla/5.0")
            .requestFactory(factory)
            .build();
  }

  public Map<Integer, Set<LocalDate>> annualHolidays() {
    try {
      String body = client.get().uri(PAGE).retrieve().body(String.class);
      var tables =
          Jsoup.parse(body == null ? "" : body).select("table").stream()
              .filter(
                  table ->
                      "Holiday"
                          .equals(
                              table.select("thead th").first() == null
                                  ? ""
                                  : table.select("thead th").first().text()))
              .toList();
      if (tables.size() != 1) throw new IllegalArgumentException("Missing holiday table");
      Element table = tables.getFirst();
      var headers = table.select("thead th");
      Map<Integer, Set<LocalDate>> calendar = new HashMap<>();
      var years = new java.util.ArrayList<Integer>();
      for (int column = 1; column < headers.size(); column++) {
        String heading = headers.get(column).text();
        if (!heading.matches("\\d{4}")) throw new IllegalArgumentException("Invalid calendar year");
        int year = Integer.parseInt(heading);
        if (calendar.put(year, new HashSet<>()) != null)
          throw new IllegalArgumentException("Duplicate calendar year");
        years.add(year);
      }
      if (years.isEmpty()) throw new IllegalArgumentException("Missing calendar years");
      Set<String> holidays = new HashSet<>();
      for (Element row : table.select("tbody tr")) {
        var names = row.select("th");
        var cells = row.select("td");
        if (names.size() != 1 || cells.size() != years.size())
          throw new IllegalArgumentException("Incomplete holiday row");
        String name = names.getFirst().text().replace('’', '\'');
        if (!holidays.add(name)) throw new IllegalArgumentException("Duplicate holiday row");
        for (int column = 0; column < years.size(); column++) {
          int year = years.get(column);
          String text = cells.get(column).text();
          // NYSE는 토요일인 1월 1일을 전년 금요일의 대체 휴장일로 지정하지 않는다.
          if (name.equals("New Year's Day")
              && text.matches("—\\**")
              && LocalDate.of(year, 1, 1).getDayOfWeek() == DayOfWeek.SATURDAY) continue;
          var date = DATE.matcher(text);
          if (!date.matches()) throw new IllegalArgumentException("Invalid holiday date");
          LocalDate holiday = LocalDate.parse(date.group(1) + " " + year, DATE_FORMAT);
          if (!calendar.get(year).add(holiday))
            throw new IllegalArgumentException("Duplicate holiday date");
        }
      }
      if (!holidays.containsAll(REQUIRED_HOLIDAYS))
        throw new IllegalArgumentException("Incomplete annual calendar");
      calendar.replaceAll((year, dates) -> Set.copyOf(dates));
      return Map.copyOf(calendar);
    } catch (RuntimeException exception) {
      log.warn("NYSE calendar fetch failed: errorType={}", exception.getClass().getSimpleName());
      throw new BusinessException(ExternalErrorCode.NYSE_CALENDAR_REQUEST_FAILED);
    }
  }
}
