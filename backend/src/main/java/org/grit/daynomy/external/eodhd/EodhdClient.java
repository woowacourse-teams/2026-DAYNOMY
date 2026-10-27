package org.grit.daynomy.external.eodhd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.RetryableExternalRequestException;
import org.grit.daynomy.external.eodhd.dto.EodhdDailyPrice;
import org.grit.daynomy.external.eodhd.dto.EodhdSymbol;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class EodhdClient {
  private static final ObjectMapper JSON = new ObjectMapper();
  private final EodhdProperties properties;
  private final RestClient restClient;

  public EodhdClient(EodhdProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }

  public List<EodhdSymbol> getUsSymbols() {
    requireToken();
    try {
      EodhdSymbol[] result =
          restClient
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/exchange-symbol-list/US")
                          .queryParam("api_token", "{token}")
                          .queryParam("fmt", "json")
                          .build(properties.apiToken()))
              .retrieve()
              .body(EodhdSymbol[].class);
      return validatedList(result);
    } catch (ResourceAccessException exception) {
      throw new RetryableExternalRequestException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    } catch (RestClientResponseException exception) {
      if (exception.getStatusCode().is5xxServerError()
          || exception.getStatusCode().value() == 429) {
        throw new RetryableExternalRequestException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
      }
      throw new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    } catch (RestClientException exception) {
      // Exceptions contain the request URL (including api_token); never retain or log them.
      throw new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    }
  }

  public List<EodhdDailyPrice> getUsDailyPrices(LocalDate date) {
    requireToken();
    try {
      EodhdDailyPrice[] result =
          restClient
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/eod-bulk-last-day/US")
                          .queryParam("api_token", "{token}")
                          .queryParam("fmt", "json")
                          .queryParam("date", date)
                          .build(properties.apiToken()))
              .retrieve()
              .body(EodhdDailyPrice[].class);
      return validatedList(result);
    } catch (RestClientException exception) {
      throw new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    }
  }

  public List<EodhdDailyPrice> getHistoricalPrices(String assetCode, LocalDate from, LocalDate to) {
    requireToken();
    try {
      EodhdDailyPrice[] result =
          restClient
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/eod/{assetCode}")
                          .queryParam("api_token", "{token}")
                          .queryParam("fmt", "json")
                          .queryParam("from", from)
                          .queryParam("to", to)
                          .build(Map.of("assetCode", assetCode, "token", properties.apiToken())))
              .retrieve()
              .body(EodhdDailyPrice[].class);
      return validatedList(result);
    } catch (RestClientException exception) {
      throw new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    }
  }

  public Set<LocalDate> getUsMarketHolidays(LocalDate from, LocalDate to) {
    requireToken();
    try {
      String body =
          restClient
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/exchange-details/US")
                          .queryParam("api_token", "{token}")
                          .queryParam("fmt", "json")
                          .queryParam("from", from)
                          .queryParam("to", to)
                          .build(properties.apiToken()))
              .retrieve()
              .body(String.class);
      JsonNode response = body == null ? null : JSON.readTree(body);
      JsonNode holidays = response == null ? null : response.get("ExchangeHolidays");
      if (response == null
          || !"US".equalsIgnoreCase(response.path("Code").asText())
          || holidays == null
          || !holidays.isContainerNode()) {
        throw new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
      }
      Set<LocalDate> dates = new HashSet<>();
      for (JsonNode holiday : holidays) {
        if (!"official".equalsIgnoreCase(holiday.path("Type").asText())) continue;
        LocalDate date = LocalDate.parse(holiday.path("Date").asText());
        if (!date.isBefore(from) && !date.isAfter(to)) dates.add(date);
      }
      return Set.copyOf(dates);
    } catch (RestClientException | DateTimeException | IOException exception) {
      throw new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    }
  }

  public List<EodhdDailyPrice> getUsdKrwRates(LocalDate from, LocalDate to) {
    requireToken();
    try {
      EodhdDailyPrice[] result =
          restClient
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/eod/USDKRW.FOREX")
                          .queryParam("api_token", "{token}")
                          .queryParam("fmt", "json")
                          .queryParam("from", from)
                          .queryParam("to", to)
                          .build(properties.apiToken()))
              .retrieve()
              .body(EodhdDailyPrice[].class);
      return validatedList(result);
    } catch (RestClientException exception) {
      throw new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    }
  }

  private void requireToken() {
    if (properties.apiToken() == null || properties.apiToken().isBlank()) {
      throw new BusinessException(ExternalErrorCode.EODHD_API_NOT_CONFIGURED);
    }
  }

  private <T> List<T> validatedList(T[] result) {
    if (result == null || Arrays.stream(result).anyMatch(java.util.Objects::isNull)) {
      throw new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    }
    return List.of(result);
  }
}
