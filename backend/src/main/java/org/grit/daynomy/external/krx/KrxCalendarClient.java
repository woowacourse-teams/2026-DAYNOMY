package org.grit.daynomy.external.krx;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;

@Component
public class KrxCalendarClient {
  private static final String PAGE = "/contents/MKD/01/0110/01100305/MKD01100305.jsp";
  private static final String DATASET = "MKD/01/0110/01100305/mkd01100305_01";
  private static final ObjectMapper JSON = new ObjectMapper();
  private static final Pattern YEAR_SELECT =
      Pattern.compile(
          "<select\\b[^>]*\\bname=[\"']search_bas_yy[\"'][^>]*>(.*?)</select>",
          Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
  private static final Pattern YEAR_OPTION =
      Pattern.compile("<option\\b[^>]*\\bvalue=[\"'](\\d{4})[\"']", Pattern.CASE_INSENSITIVE);
  private final RestClient client;

  public KrxCalendarClient(KrxCalendarProperties properties, RestClient.Builder restClientBuilder) {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(properties.connectTimeout());
    factory.setReadTimeout(properties.readTimeout());
    client =
        restClientBuilder
            .baseUrl(properties.baseUrl())
            .defaultHeader("Referer", properties.baseUrl() + PAGE)
            .requestFactory(factory)
            .build();
  }

  public Set<Integer> publishedYears() {
    try {
      String html = client.get().uri(PAGE).retrieve().body(String.class);
      var select = YEAR_SELECT.matcher(html == null ? "" : html);
      if (!select.find()) throw new IllegalArgumentException("Missing calendar years");
      var options = YEAR_OPTION.matcher(select.group(1));
      Set<Integer> years = new HashSet<>();
      while (options.find()) years.add(Integer.parseInt(options.group(1)));
      if (years.isEmpty()) throw new IllegalArgumentException("Empty calendar years");
      return Set.copyOf(years);
    } catch (RuntimeException exception) {
      throw new BusinessException(ExternalErrorCode.KRX_CALENDAR_REQUEST_FAILED);
    }
  }

  public Set<LocalDate> holidays(int year) {
    try {
      String code =
          client
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/contents/COM/GenerateOTP.jspx")
                          .queryParam("bld", DATASET)
                          .queryParam("name", "form")
                          .build())
              .retrieve()
              .body(String.class);
      if (code == null || !code.strip().matches("[A-Za-z0-9+/=_-]{1,1024}"))
        throw new IllegalArgumentException("Invalid calendar response code");
      var form = new LinkedMultiValueMap<String, String>();
      form.add("search_bas_yy", String.valueOf(year));
      form.add("gridTp", "KRX");
      form.add("code", code.strip());
      String body =
          client
              .post()
              .uri("/contents/OPN/99/OPN99000001.jspx")
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .body(form)
              .retrieve()
              .body(String.class);
      // KRX는 JSON을 text/html로 반환하므로 본문을 직접 검증한다.
      JsonNode rows = JSON.readTree(body).path("block1");
      if (!rows.isArray() || rows.isEmpty())
        throw new IllegalArgumentException("Missing calendar holidays");
      Set<LocalDate> holidays = new HashSet<>();
      for (JsonNode row : rows) {
        LocalDate date = LocalDate.parse(row.path("calnd_dd").asText());
        if (date.getYear() != year || !holidays.add(date))
          throw new IllegalArgumentException("Invalid calendar date");
      }
      LocalDate lastWeekday = LocalDate.of(year, 12, 31);
      while (lastWeekday.getDayOfWeek().getValue() > DayOfWeek.FRIDAY.getValue())
        lastWeekday = lastWeekday.minusDays(1);
      // 고정 휴장일이 빠진 응답으로 나머지 평일을 거래일로 확정하지 않는다.
      for (LocalDate fixed :
          Set.of(
              LocalDate.of(year, 1, 1),
              LocalDate.of(year, 5, 1),
              LocalDate.of(year, 12, 25),
              lastWeekday)) {
        if (fixed.getDayOfWeek().getValue() <= 5 && !holidays.contains(fixed))
          throw new IllegalArgumentException("Incomplete annual calendar");
      }
      return Set.copyOf(holidays);
    } catch (IOException | RuntimeException exception) {
      throw new BusinessException(ExternalErrorCode.KRX_CALENDAR_REQUEST_FAILED);
    }
  }
}
