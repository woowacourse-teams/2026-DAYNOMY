package org.grit.daynomy.investmentcalendar.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.service.InvestmentEventEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

@Component
public class BlsCpiProvider implements InvestmentCalendarProvider {

  private static final String CPI_SERIES_ID = "CUUR0000SA0";
  private static final ZoneId EASTERN = ZoneId.of("America/New_York");
  private static final DateTimeFormatter DATE_FORMAT =
      DateTimeFormatter.ofPattern("EEEE, MMMM d, uuuu", Locale.US);
  private static final DateTimeFormatter TIME_FORMAT =
      DateTimeFormatter.ofPattern("h:mm a", Locale.US);
  private static final DateTimeFormatter REFERENCE_MONTH_FORMAT =
      DateTimeFormatter.ofPattern("MMMM uuuu", Locale.US);

  private final InvestmentCalendarExternalProperties properties;
  private final RestClient apiClient;
  private final RestClient scheduleClient;

  public BlsCpiProvider(InvestmentCalendarExternalProperties properties) {
    this.properties = properties;
    this.apiClient =
        InvestmentCalendarHttpClientFactory.create(properties.blsBaseUrl(), properties);
    this.scheduleClient =
        InvestmentCalendarHttpClientFactory.create(properties.blsScheduleUrl(), properties);
  }

  @Override
  public List<InvestmentEventEntry> fetch(LocalDate today, int historyYears, int dartLookbackDays) {
    int firstReleaseYear = today.getYear() - historyYears;
    int lastReleaseYear = today.getYear() + 1;
    List<CpiRelease> releases = new ArrayList<>();
    for (int year = firstReleaseYear; year <= lastReleaseYear; year++) {
      releases.addAll(fetchSchedule(year));
    }

    int firstDataYear = firstReleaseYear - 2;
    Map<YearMonth, BigDecimal> indexes = fetchIndexes(firstDataYear, lastReleaseYear);
    return releases.stream()
        .sorted(Comparator.comparing(CpiRelease::announcedAt))
        .map(release -> entry(release, indexes))
        .toList();
  }

  private List<CpiRelease> fetchSchedule(int year) {
    String url = scheduleUrl(year);
    String html;
    try {
      html = scheduleClient.get().uri(url).retrieve().body(String.class);
    } catch (HttpClientErrorException.NotFound exception) {
      return List.of();
    }
    if (html == null || html.isBlank()) {
      return List.of();
    }

    Document document = Jsoup.parse(html, url);
    Map<YearMonth, CpiRelease> releases = new LinkedHashMap<>();
    for (Element row : document.select("tr")) {
      List<Element> cells = row.select("td");
      if (cells.size() < 3) {
        continue;
      }
      String title = cells.get(2).text().trim();
      if (!title.startsWith("Consumer Price Index for ")) {
        continue;
      }
      try {
        LocalDate date = LocalDate.parse(cells.get(0).text().trim(), DATE_FORMAT);
        LocalTime time = LocalTime.parse(cells.get(1).text().trim(), TIME_FORMAT);
        YearMonth referenceMonth =
            YearMonth.parse(
                title.substring("Consumer Price Index for ".length()).trim(),
                REFERENCE_MONTH_FORMAT);
        releases.put(
            referenceMonth,
            new CpiRelease(
                referenceMonth, LocalDateTime.of(date, time).atZone(EASTERN).toInstant()));
      } catch (DateTimeParseException ignored) {
        // BLS rows that do not contain a complete release date are not CPI schedules.
      }
    }
    return List.copyOf(releases.values());
  }

  private Map<YearMonth, BigDecimal> fetchIndexes(int startYear, int endYear) {
    BlsResponse response =
        apiClient
            .post()
            .uri("/timeseries/data/")
            .contentType(MediaType.APPLICATION_JSON)
            .body(
                Map.of(
                    "seriesid", List.of(CPI_SERIES_ID), "startyear", startYear, "endyear", endYear))
            .retrieve()
            .body(BlsResponse.class);
    if (response == null
        || !"REQUEST_SUCCEEDED".equals(response.status())
        || response.results() == null) {
      throw new IllegalStateException("BLS CPI response was not successful");
    }

    Map<YearMonth, BigDecimal> indexes = new HashMap<>();
    for (BlsSeries series : response.results().series()) {
      for (BlsData data : series.data()) {
        if (data.period() == null || !data.period().matches("M(0[1-9]|1[0-2])")) {
          continue;
        }
        try {
          indexes.put(
              YearMonth.of(
                  Integer.parseInt(data.year()), Integer.parseInt(data.period().substring(1))),
              new BigDecimal(data.value()));
        } catch (NumberFormatException ignored) {
          // BLS may return "-" when a monthly value is unavailable.
        }
      }
    }
    return indexes;
  }

  private InvestmentEventEntry entry(CpiRelease release, Map<YearMonth, BigDecimal> indexes) {
    BigDecimal actual = yearOverYear(release.referenceMonth(), indexes);
    BigDecimal previous = yearOverYear(release.referenceMonth().minusMonths(1), indexes);
    return new InvestmentEventEntry(
        InvestmentEventType.US_CPI,
        "미국 소비자물가지수 발표",
        release.announcedAt(),
        previous,
        actual,
        "%",
        "미국 노동통계국(BLS)",
        properties.blsScheduleUrl(),
        "BLS-CPI-" + release.referenceMonth(),
        null);
  }

  private BigDecimal yearOverYear(YearMonth month, Map<YearMonth, BigDecimal> indexes) {
    BigDecimal current = indexes.get(month);
    BigDecimal previousYear = indexes.get(month.minusYears(1));
    if (current == null || previousYear == null || previousYear.signum() == 0) {
      return null;
    }
    return current
        .subtract(previousYear)
        .multiply(BigDecimal.valueOf(100))
        .divide(previousYear, 2, RoundingMode.HALF_UP);
  }

  private String scheduleUrl(int year) {
    URI configured = URI.create(properties.blsScheduleUrl());
    return configured.resolve("/schedule/" + year + "/home.htm").toString();
  }

  private record CpiRelease(YearMonth referenceMonth, Instant announcedAt) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BlsResponse(String status, @JsonProperty("Results") BlsResults results) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BlsResults(List<BlsSeries> series) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BlsSeries(List<BlsData> data) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record BlsData(String year, String period, String value) {}
}
