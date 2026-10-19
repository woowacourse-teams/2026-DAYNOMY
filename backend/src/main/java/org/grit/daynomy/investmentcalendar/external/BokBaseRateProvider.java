package org.grit.daynomy.investmentcalendar.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.service.InvestmentEventEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class BokBaseRateProvider implements InvestmentCalendarProvider {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final LocalTime MEETING_TIME = LocalTime.of(9, 0);
  private static final Pattern MEETING_DATE_PATTERN = Pattern.compile("(\\d{1,2})월\\s*(\\d{1,2})일");
  private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String SCHEDULE_PATH =
      "/portal/singl/crncyPolicyDrcMtg/listYear.do?menuNo=200755&mtgSe=A&pYear={year}";
  private static final String SCHEDULE_SOURCE_URL =
      "https://www.bok.or.kr/portal/main/contents.do?menuNo=200755";

  private final InvestmentCalendarExternalProperties properties;
  private final RestClient bokClient;
  private final RestClient ecosClient;

  public BokBaseRateProvider(InvestmentCalendarExternalProperties properties) {
    this.properties = properties;
    this.bokClient =
        InvestmentCalendarHttpClientFactory.create(properties.bokBaseUrl(), properties);
    this.ecosClient =
        InvestmentCalendarHttpClientFactory.create(properties.ecosBaseUrl(), properties);
  }

  @Override
  public List<InvestmentEventEntry> fetch(LocalDate today, int historyYears, int dartLookbackDays) {
    if (properties.ecosApiKey() == null || properties.ecosApiKey().isBlank()) {
      throw new IllegalStateException("ECOS API key is not configured");
    }

    int firstYear = today.getYear() - historyYears;
    int lastYear = today.getYear() + 1;
    List<LocalDate> meetings = new ArrayList<>();
    for (int year = firstYear; year <= lastYear; year++) {
      meetings.addAll(fetchMeetings(year));
    }
    meetings = meetings.stream().distinct().sorted().toList();
    if (meetings.isEmpty()) {
      return List.of();
    }

    NavigableMap<LocalDate, BigDecimal> rates =
        fetchRates(meetings.getFirst().minusDays(7), today.plusDays(1));
    return meetings.stream()
        .sorted(Comparator.naturalOrder())
        .map(meeting -> entry(meeting, rates))
        .toList();
  }

  private List<LocalDate> fetchMeetings(int year) {
    String html = bokClient.get().uri(SCHEDULE_PATH, year).retrieve().body(String.class);
    if (html == null || html.isBlank()) {
      return List.of();
    }

    Document document = Jsoup.parse(html);
    List<LocalDate> meetings = new ArrayList<>();
    for (Element header : document.select("th[scope=row]")) {
      Matcher matcher = MEETING_DATE_PATTERN.matcher(header.text());
      if (matcher.find()) {
        meetings.add(
            LocalDate.of(
                year, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2))));
      }
    }
    return meetings;
  }

  private NavigableMap<LocalDate, BigDecimal> fetchRates(LocalDate from, LocalDate to) {
    EcosResponse response =
        ecosClient
            .get()
            .uri(
                "/StatisticSearch/{key}/json/kr/1/10000/722Y001/D/{from}/{to}/0101000",
                properties.ecosApiKey(),
                from.format(BASIC_DATE),
                to.format(BASIC_DATE))
            .retrieve()
            .body(EcosResponse.class);
    if (response == null || response.statistics() == null) {
      throw new IllegalStateException("ECOS base-rate response was not successful");
    }

    NavigableMap<LocalDate, BigDecimal> rates = new TreeMap<>();
    for (EcosRow row : response.statistics().rows()) {
      rates.put(LocalDate.parse(row.time(), BASIC_DATE), new BigDecimal(row.dataValue()));
    }
    return rates;
  }

  private InvestmentEventEntry entry(LocalDate meeting, NavigableMap<LocalDate, BigDecimal> rates) {
    BigDecimal previous = valueAt(rates, meeting.minusDays(1));
    BigDecimal actual = rates.get(meeting);
    return new InvestmentEventEntry(
        InvestmentEventType.KOREA_BASE_RATE,
        "한국 기준금리 결정",
        meeting.atTime(MEETING_TIME).atZone(SEOUL).toInstant(),
        previous,
        actual,
        "%",
        "한국은행",
        SCHEDULE_SOURCE_URL,
        "BOK-BASE-RATE-" + meeting,
        null);
  }

  private BigDecimal valueAt(NavigableMap<LocalDate, BigDecimal> rates, LocalDate date) {
    var entry = rates.floorEntry(date);
    return entry == null ? null : entry.getValue();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EcosResponse(@JsonProperty("StatisticSearch") EcosStatistics statistics) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EcosStatistics(@JsonProperty("row") List<EcosRow> rows) {
    private EcosStatistics {
      if (rows == null) {
        rows = List.of();
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record EcosRow(
      @JsonProperty("TIME") String time, @JsonProperty("DATA_VALUE") String dataValue) {}
}
