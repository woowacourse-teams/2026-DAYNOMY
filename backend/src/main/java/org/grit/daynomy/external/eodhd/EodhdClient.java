package org.grit.daynomy.external.eodhd;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.eodhd.dto.EodhdDailyPrice;
import org.grit.daynomy.external.eodhd.dto.EodhdSymbol;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class EodhdClient {
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
