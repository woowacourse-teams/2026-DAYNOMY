package org.grit.daynomy.external.publicdata;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.publicdata.dto.PublicDataEtfPriceResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class PublicDataEtfPriceClient {

  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

  private final PublicDataProperties properties;
  private final RestClient restClient;

  public PublicDataEtfPriceClient(PublicDataProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(properties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(properties.readTimeout()));
    this.restClient =
        RestClient.builder()
            .baseUrl(properties.etfPriceUrl())
            .requestFactory(requestFactory)
            .build();
  }

  public PublicDataEtfPriceResponse getEtfPrices(LocalDate baseDate, int pageNo, int numOfRows) {
    long startedAt = System.nanoTime();
    log.atDebug()
        .addKeyValue("event", LogEvent.EXTERNAL_API_REQUESTED.code())
        .addKeyValue("api", "PUBLIC_DATA_ETF_PRICE")
        .addKeyValue("operation", "getEtfPrices")
        .addKeyValue("baseDate", baseDate)
        .addKeyValue("pageNo", pageNo)
        .log(LogEvent.EXTERNAL_API_REQUESTED.message());

    try {
      PublicDataEtfPriceResponse response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .queryParam("serviceKey", "{serviceKey}")
                          .queryParam("numOfRows", numOfRows)
                          .queryParam("pageNo", pageNo)
                          .queryParam("resultType", "json")
                          .queryParam("basDt", baseDate.format(BASIC_DATE_FORMAT))
                          .build(normalizedServiceKey()))
              .retrieve()
              .body(PublicDataEtfPriceResponse.class);
      validateResponse(response);
      log.atInfo()
          .addKeyValue("event", LogEvent.EXTERNAL_API_COMPLETED.code())
          .addKeyValue("api", "PUBLIC_DATA_ETF_PRICE")
          .addKeyValue("operation", "getEtfPrices")
          .addKeyValue("baseDate", baseDate)
          .addKeyValue("pageNo", pageNo)
          .addKeyValue("responseCount", response.items().size())
          .addKeyValue("responseStatus", response.header().resultCode())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.EXTERNAL_API_COMPLETED.message());
      return response;
    } catch (HttpStatusCodeException exception) {
      log.atError()
          .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
          .addKeyValue("api", "PUBLIC_DATA_ETF_PRICE")
          .addKeyValue("operation", "getEtfPrices")
          .addKeyValue("baseDate", baseDate)
          .addKeyValue("pageNo", pageNo)
          .addKeyValue("responseStatus", String.valueOf(exception.getStatusCode().value()))
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorCode", ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED.code())
          .addKeyValue("exception", exception.getClass().getSimpleName())
          .log(LogEvent.EXTERNAL_API_FAILED.message());
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    } catch (RestClientException exception) {
      log.atError()
          .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
          .addKeyValue("api", "PUBLIC_DATA_ETF_PRICE")
          .addKeyValue("operation", "getEtfPrices")
          .addKeyValue("baseDate", baseDate)
          .addKeyValue("pageNo", pageNo)
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorCode", ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED.code())
          .addKeyValue("exception", exception.getClass().getSimpleName())
          .log(LogEvent.EXTERNAL_API_FAILED.message());
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    } catch (BusinessException exception) {
      log.atError()
          .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
          .addKeyValue("api", "PUBLIC_DATA_ETF_PRICE")
          .addKeyValue("operation", "getEtfPrices")
          .addKeyValue("baseDate", baseDate)
          .addKeyValue("pageNo", pageNo)
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorCode", exception.errorCode().code())
          .addKeyValue("reasonCode", "INVALID_RESPONSE")
          .log(LogEvent.EXTERNAL_API_FAILED.message());
      throw exception;
    }
  }

  private void validateResponse(PublicDataEtfPriceResponse response) {
    if (response == null || response.header() == null || response.body() == null) {
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
    if (!"00".equals(response.header().resultCode())) {
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
  }

  String normalizedServiceKey() {
    String serviceKey = properties.serviceKey();
    if (serviceKey.contains("%")) {
      return URLDecoder.decode(serviceKey, StandardCharsets.UTF_8);
    }
    return serviceKey;
  }

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }
}
