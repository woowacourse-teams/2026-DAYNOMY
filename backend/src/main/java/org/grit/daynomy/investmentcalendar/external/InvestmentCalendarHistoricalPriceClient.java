package org.grit.daynomy.investmentcalendar.external;

import java.math.BigDecimal;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.external.publicdata.PublicDataProperties;
import org.grit.daynomy.external.publicdata.dto.PublicDataEtfPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataEtfPriceResponse;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceItem;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class InvestmentCalendarHistoricalPriceClient {

  private static final int PAGE_SIZE = 1000;
  private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

  private final PublicDataProperties publicDataProperties;
  private final RestClient stockClient;
  private final RestClient etfClient;

  public InvestmentCalendarHistoricalPriceClient(
      PublicDataProperties publicDataProperties,
      InvestmentCalendarExternalProperties calendarProperties) {
    this.publicDataProperties = publicDataProperties;
    this.stockClient =
        InvestmentCalendarHttpClientFactory.create(
            publicDataProperties.stockPriceUrl(), calendarProperties);
    this.etfClient =
        InvestmentCalendarHttpClientFactory.create(
            publicDataProperties.etfPriceUrl(), calendarProperties);
  }

  public List<HistoricalPrice> fetch(
      AssetCategory category, String assetCode, LocalDate from, LocalDate to) {
    if (publicDataProperties.serviceKey() == null || publicDataProperties.serviceKey().isBlank()) {
      throw new IllegalStateException("Public data API key is not configured");
    }
    if (category == AssetCategory.STOCK) {
      return fetchStockPrices(assetCode, from, to);
    }
    if (category == AssetCategory.ETF) {
      return fetchEtfPrices(assetCode, from, to);
    }
    return List.of();
  }

  private List<HistoricalPrice> fetchStockPrices(String assetCode, LocalDate from, LocalDate to) {
    List<HistoricalPrice> prices = new ArrayList<>();
    int page = 1;
    int totalPages;
    do {
      PublicDataStockPriceResponse response = fetchStockPage(assetCode, from, to, page);
      validate(response);
      response.items().stream()
          .filter(item -> assetCode.equals(item.srtnCd()))
          .map(this::toHistoricalPrice)
          .filter(java.util.Objects::nonNull)
          .forEach(prices::add);
      totalPages = pages(response.body().totalCount());
      page++;
    } while (page <= totalPages);
    return sorted(prices);
  }

  private PublicDataStockPriceResponse fetchStockPage(
      String assetCode, LocalDate from, LocalDate to, int page) {
    return stockClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .queryParam("serviceKey", "{serviceKey}")
                    .queryParam("resultType", "json")
                    .queryParam("numOfRows", PAGE_SIZE)
                    .queryParam("pageNo", page)
                    .queryParam("likeSrtnCd", assetCode)
                    .queryParam("beginBasDt", from.format(BASIC_DATE))
                    .queryParam("endBasDt", to.format(BASIC_DATE))
                    .build(normalizedServiceKey()))
        .retrieve()
        .body(PublicDataStockPriceResponse.class);
  }

  private List<HistoricalPrice> fetchEtfPrices(String assetCode, LocalDate from, LocalDate to) {
    List<HistoricalPrice> prices = new ArrayList<>();
    int page = 1;
    int totalPages;
    do {
      PublicDataEtfPriceResponse response = fetchEtfPage(assetCode, from, to, page);
      validate(response);
      response.items().stream()
          .filter(item -> assetCode.equals(item.srtnCd()))
          .map(this::toHistoricalPrice)
          .filter(java.util.Objects::nonNull)
          .forEach(prices::add);
      totalPages = pages(response.body().totalCount());
      page++;
    } while (page <= totalPages);
    return sorted(prices);
  }

  private PublicDataEtfPriceResponse fetchEtfPage(
      String assetCode, LocalDate from, LocalDate to, int page) {
    return etfClient
        .get()
        .uri(
            uriBuilder ->
                uriBuilder
                    .queryParam("serviceKey", "{serviceKey}")
                    .queryParam("resultType", "json")
                    .queryParam("numOfRows", PAGE_SIZE)
                    .queryParam("pageNo", page)
                    .queryParam("likeSrtnCd", assetCode)
                    .queryParam("beginBasDt", from.format(BASIC_DATE))
                    .queryParam("endBasDt", to.format(BASIC_DATE))
                    .build(normalizedServiceKey()))
        .retrieve()
        .body(PublicDataEtfPriceResponse.class);
  }

  private HistoricalPrice toHistoricalPrice(PublicDataStockPriceItem item) {
    return parse(item.basDt(), item.clpr());
  }

  private HistoricalPrice toHistoricalPrice(PublicDataEtfPriceItem item) {
    return parse(item.basDt(), item.clpr());
  }

  private HistoricalPrice parse(String date, String closePrice) {
    try {
      BigDecimal price = new BigDecimal(closePrice);
      if (price.signum() <= 0) {
        return null;
      }
      return new HistoricalPrice(LocalDate.parse(date, BASIC_DATE), price);
    } catch (RuntimeException exception) {
      return null;
    }
  }

  private void validate(PublicDataStockPriceResponse response) {
    if (response == null
        || response.header() == null
        || response.body() == null
        || !"00".equals(response.header().resultCode())) {
      throw new IllegalStateException("Public stock price response was not successful");
    }
  }

  private void validate(PublicDataEtfPriceResponse response) {
    if (response == null
        || response.header() == null
        || response.body() == null
        || !"00".equals(response.header().resultCode())) {
      throw new IllegalStateException("Public ETF price response was not successful");
    }
  }

  private int pages(int totalCount) {
    return Math.max((totalCount + PAGE_SIZE - 1) / PAGE_SIZE, 1);
  }

  private List<HistoricalPrice> sorted(List<HistoricalPrice> prices) {
    Map<LocalDate, HistoricalPrice> pricesByDate = new TreeMap<>();
    for (HistoricalPrice price : prices) {
      pricesByDate.put(price.baseDate(), price);
    }
    return List.copyOf(pricesByDate.values());
  }

  private String normalizedServiceKey() {
    String serviceKey = publicDataProperties.serviceKey();
    if (serviceKey.contains("%")) {
      return URLDecoder.decode(serviceKey, StandardCharsets.UTF_8);
    }
    return serviceKey;
  }

  public record HistoricalPrice(LocalDate baseDate, BigDecimal closePrice) {}
}
