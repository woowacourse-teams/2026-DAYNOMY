package org.grit.daynomy.investmentcalendar.external;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventType;
import org.grit.daynomy.investmentcalendar.service.InvestmentEventEntry;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class OpenDartEarningsProvider implements InvestmentCalendarProvider {

  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final LocalTime DEFAULT_ANNOUNCEMENT_TIME = LocalTime.of(9, 0);
  private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String DART_VIEWER_URL = "https://dart.fss.or.kr/dsaf001/main.do?rcpNo=";

  private final InvestmentCalendarExternalProperties properties;
  private final RestClient restClient;

  public OpenDartEarningsProvider(InvestmentCalendarExternalProperties properties) {
    this.properties = properties;
    this.restClient =
        InvestmentCalendarHttpClientFactory.create(properties.dartBaseUrl(), properties);
  }

  @Override
  public List<InvestmentEventEntry> fetch(LocalDate today, int historyYears, int dartLookbackDays) {
    if (properties.dartApiKey() == null || properties.dartApiKey().isBlank()) {
      throw new IllegalStateException("OpenDART API key is not configured");
    }

    LocalDate from = today.minusDays(dartLookbackDays);
    Map<String, InvestmentEventEntry> entries = new LinkedHashMap<>();
    int page = 1;
    int totalPages;
    do {
      DartListResponse response = fetchPage(from, today, page);
      if ("013".equals(response.status())) {
        return List.of();
      }
      if (!"000".equals(response.status())) {
        throw new IllegalStateException("OpenDART disclosure response was not successful");
      }
      log.debug(
          "Fetched OpenDART disclosure page: page={}, totalPages={}, disclosureCount={}",
          page,
          response.totalPage(),
          response.list().size());
      for (DartDisclosure disclosure : response.list()) {
        parse(disclosure)
            .ifPresent(entry -> entries.merge(entry.sourceKey(), entry, this::preferResult));
      }
      totalPages = Math.max(response.totalPage(), 1);
      page++;
    } while (page <= totalPages);
    return List.copyOf(entries.values());
  }

  private DartListResponse fetchPage(LocalDate from, LocalDate to, int page) {
    DartListResponse response =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/list.json")
                        .queryParam("crtfc_key", properties.dartApiKey())
                        .queryParam("bgn_de", from.format(BASIC_DATE))
                        .queryParam("end_de", to.format(BASIC_DATE))
                        .queryParam("pblntf_ty", "I")
                        .queryParam("last_reprt_at", "Y")
                        .queryParam("sort", "date")
                        .queryParam("sort_mth", "desc")
                        .queryParam("page_no", page)
                        .queryParam("page_count", 100)
                        .build())
            .retrieve()
            .body(DartListResponse.class);
    if (response == null) {
      throw new IllegalStateException("OpenDART disclosure response was empty");
    }
    return response;
  }

  private Optional<InvestmentEventEntry> parse(DartDisclosure disclosure) {
    if (disclosure.stockCode() == null || disclosure.stockCode().isBlank()) {
      return Optional.empty();
    }

    String reportName = disclosure.reportName().replaceAll("\\s+", "");
    if (!reportName.contains("결산실적공시예고") && !reportName.contains("영업(잠정)실적(공정공시)")) {
      return Optional.empty();
    }

    Document document = fetchDocument(disclosure.receiptNumber());
    Optional<InvestmentEventEntry> entry =
        reportName.contains("결산실적공시예고")
            ? parseAdvanceNotice(disclosure, document)
            : parseResult(disclosure, document);
    if (entry.isEmpty()) {
      log.debug(
          "Skipped unsupported OpenDART earnings document: receiptNumber={}, reportName={}",
          disclosure.receiptNumber(),
          reportName);
    }
    return entry;
  }

  private Optional<InvestmentEventEntry> parseAdvanceNotice(
      DartDisclosure disclosure, Document document) {
    LocalDate periodEnd = findDate(document, "종료일").orElse(null);
    LocalDate scheduledDate = findDate(document, "결산실적 공시예정일").orElse(null);
    if (periodEnd == null || scheduledDate == null) {
      return Optional.empty();
    }
    return Optional.of(entry(disclosure, periodEnd, scheduledDate, null, null, "원"));
  }

  private Optional<InvestmentEventEntry> parseResult(DartDisclosure disclosure, Document document) {
    DateRange period = findCurrentPeriod(document).orElse(null);
    if (period == null
        || Duration.between(period.start().atStartOfDay(), period.end().atStartOfDay()).toDays()
            < 70) {
      return Optional.empty();
    }

    EarningsValue earnings = findOperatingProfit(document).orElse(new EarningsValue(null, null));
    String unit = findUnit(document);
    LocalDate announcedDate = LocalDate.parse(disclosure.receiptDate(), BASIC_DATE);
    return Optional.of(
        entry(
            disclosure, period.end(), announcedDate, earnings.previous(), earnings.actual(), unit));
  }

  private InvestmentEventEntry entry(
      DartDisclosure disclosure,
      LocalDate periodEnd,
      LocalDate announcedDate,
      BigDecimal previous,
      BigDecimal actual,
      String unit) {
    return new InvestmentEventEntry(
        InvestmentEventType.CORPORATE_EARNINGS,
        disclosure.corporationName() + " " + quarterLabel(periodEnd) + " 실적 발표",
        announcedDate.atTime(DEFAULT_ANNOUNCEMENT_TIME).atZone(SEOUL).toInstant(),
        previous,
        actual,
        unit,
        "전자공시시스템(DART)",
        DART_VIEWER_URL + disclosure.receiptNumber(),
        "DART-EARNINGS-" + disclosure.stockCode() + "-" + periodEnd,
        disclosure.stockCode());
  }

  private Document fetchDocument(String receiptNumber) {
    byte[] response =
        restClient
            .get()
            .uri(
                uriBuilder ->
                    uriBuilder
                        .path("/document.xml")
                        .queryParam("crtfc_key", properties.dartApiKey())
                        .queryParam("rcept_no", receiptNumber)
                        .build())
            .retrieve()
            .body(byte[].class);
    if (response == null || response.length == 0) {
      throw new IllegalStateException("OpenDART document response was empty");
    }

    try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(response))) {
      ZipEntry zipEntry = zip.getNextEntry();
      if (zipEntry == null) {
        throw new IllegalStateException("OpenDART document was not a zip file");
      }
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      zip.transferTo(output);
      return Jsoup.parse(
          new ByteArrayInputStream(output.toByteArray()),
          StandardCharsets.UTF_8.name(),
          DART_VIEWER_URL + receiptNumber);
    } catch (IOException exception) {
      throw new IllegalStateException("OpenDART document could not be read", exception);
    }
  }

  private Optional<LocalDate> findDate(Document document, String label) {
    for (Element row : document.select("tr")) {
      if (!row.text().contains(label)) {
        continue;
      }
      for (Element cell : row.select("td")) {
        Optional<LocalDate> date = parseDate(cell.text());
        if (date.isPresent()) {
          return date;
        }
      }
    }
    return Optional.empty();
  }

  private Optional<DateRange> findCurrentPeriod(Document document) {
    for (Element row : document.select("tr")) {
      List<Element> cells = row.select("td");
      if (cells.size() < 4 || !"당기실적".equals(cells.getFirst().text().trim())) {
        continue;
      }
      LocalDate start = parseDate(cells.get(1).text()).orElse(null);
      LocalDate end = parseDate(cells.get(3).text()).orElse(null);
      if (start != null && end != null) {
        return Optional.of(new DateRange(start, end));
      }
    }
    return Optional.empty();
  }

  private Optional<EarningsValue> findOperatingProfit(Document document) {
    for (Element row : document.select("tr")) {
      List<Element> cells = row.select("td");
      if (cells.size() < 4 || !"영업이익".equals(cells.getFirst().text().trim())) {
        continue;
      }
      if (!cells.get(1).text().contains("당해실적")) {
        continue;
      }
      return Optional.of(
          new EarningsValue(parseNumber(cells.get(3).text()), parseNumber(cells.get(2).text())));
    }
    return Optional.empty();
  }

  private String findUnit(Document document) {
    for (Element cell : document.select("td")) {
      String text = cell.text().replace(" ", "");
      if (text.contains("단위:백만원")) {
        return "백만원";
      }
      if (text.contains("단위:천원")) {
        return "천원";
      }
      if (text.contains("단위:원")) {
        return "원";
      }
    }
    return "원";
  }

  private Optional<LocalDate> parseDate(String text) {
    java.util.regex.Matcher matcher =
        java.util.regex.Pattern.compile("(20\\d{2})[-./](\\d{1,2})[-./](\\d{1,2})").matcher(text);
    if (!matcher.find()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          LocalDate.of(
              Integer.parseInt(matcher.group(1)),
              Integer.parseInt(matcher.group(2)),
              Integer.parseInt(matcher.group(3))));
    } catch (java.time.DateTimeException exception) {
      return Optional.empty();
    }
  }

  private BigDecimal parseNumber(String text) {
    String normalized = text.trim().replace(",", "");
    if (normalized.isBlank() || "-".equals(normalized)) {
      return null;
    }
    if (normalized.startsWith("(") && normalized.endsWith(")")) {
      normalized = "-" + normalized.substring(1, normalized.length() - 1);
    }
    try {
      return new BigDecimal(normalized);
    } catch (NumberFormatException ignored) {
      return null;
    }
  }

  private String quarterLabel(LocalDate periodEnd) {
    return switch (periodEnd.getMonthValue()) {
      case 3 -> "1분기";
      case 6 -> "2분기";
      case 9 -> "3분기";
      case 12 -> "4분기";
      default -> periodEnd.getMonthValue() + "월";
    };
  }

  private InvestmentEventEntry preferResult(InvestmentEventEntry left, InvestmentEventEntry right) {
    if (right.actualValue() != null || left.actualValue() == null) {
      return right;
    }
    return left;
  }

  private record DateRange(LocalDate start, LocalDate end) {}

  private record EarningsValue(BigDecimal previous, BigDecimal actual) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record DartListResponse(
      String status, @JsonProperty("total_page") int totalPage, List<DartDisclosure> list) {
    private DartListResponse {
      if (list == null) {
        list = List.of();
      }
    }
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  private record DartDisclosure(
      @JsonProperty("corp_name") String corporationName,
      @JsonProperty("stock_code") String stockCode,
      @JsonProperty("report_nm") String reportName,
      @JsonProperty("rcept_no") String receiptNumber,
      @JsonProperty("rcept_dt") String receiptDate) {}
}
