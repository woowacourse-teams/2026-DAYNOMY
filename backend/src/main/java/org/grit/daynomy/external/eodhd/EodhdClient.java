package org.grit.daynomy.external.eodhd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
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
      if (exception.getCause() instanceof IOException) {
        throw new RetryableExternalRequestException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
      }
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

  public DelayedQuotes getDelayedQuotes(List<String> symbols) {
    requireToken();
    try {
      String body =
          restClient
              .get()
              .uri(
                  builder -> {
                    builder
                        .path("/real-time/{symbol}")
                        .queryParam("api_token", "{token}")
                        .queryParam("fmt", "json");
                    if (symbols.size() > 1) {
                      builder.queryParam("s", "{additional}");
                    }
                    return builder.build(
                        Map.of(
                            "symbol",
                            symbols.getFirst(),
                            "token",
                            properties.apiToken(),
                            "additional",
                            String.join(",", symbols.subList(1, symbols.size()))));
                  })
              .retrieve()
              .body(String.class);
      JsonNode response = JSON.readTree(body == null ? "" : body);
      List<JsonNode> rows = new ArrayList<>();
      if (response != null && response.isArray()) response.forEach(rows::add);
      else if (response != null && response.isObject()) rows.add(response);
      List<DelayedQuote> quotes = new ArrayList<>();
      for (JsonNode row : rows) {
        JsonNode price = row.path("close");
        JsonNode timestamp = row.path("timestamp");
        if (price.isNumber()
            && price.decimalValue().signum() > 0
            && timestamp.isIntegralNumber()
            && timestamp.canConvertToLong()) {
          try {
            quotes.add(
                new DelayedQuote(
                    row.path("code").asText(),
                    price.decimalValue(),
                    Instant.ofEpochSecond(timestamp.longValue())));
          } catch (DateTimeException ignored) {
            // A malformed symbol must not discard other valid rows from the batch.
          }
        }
      }
      return new DelayedQuotes(List.copyOf(quotes), 200);
    } catch (RestClientResponseException exception) {
      return new DelayedQuotes(List.of(), exception.getStatusCode().value());
    } catch (RestClientException | IOException exception) {
      throw new BusinessException(ExternalErrorCode.EODHD_API_REQUEST_FAILED);
    }
  }

  public record DelayedQuote(String symbol, BigDecimal price, Instant asOf) {}

  public record DelayedQuotes(List<DelayedQuote> quotes, int status) {}

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
