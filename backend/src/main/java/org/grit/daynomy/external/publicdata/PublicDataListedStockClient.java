package org.grit.daynomy.external.publicdata;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.publicdata.dto.PublicDataListedStockResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PublicDataListedStockClient {

  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

  private final PublicDataProperties properties;
  private final RestClient restClient;

  public PublicDataListedStockClient(PublicDataProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(properties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(properties.readTimeout()));
    this.restClient =
        RestClient.builder()
            .baseUrl(properties.listedStockUrl())
            .requestFactory(requestFactory)
            .build();
  }

  public PublicDataListedStockResponse getListedStocks(
      LocalDate baseDate, int pageNo, int numOfRows) {
    try {
      PublicDataListedStockResponse response =
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
              .body(PublicDataListedStockResponse.class);
      validateResponse(response);
      return response;
    } catch (RestClientException exception) {
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
  }

  private void validateResponse(PublicDataListedStockResponse response) {
    if (response == null || response.header() == null || response.body() == null) {
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
    if (!"00".equals(response.header().resultCode())) {
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
  }

  private String normalizedServiceKey() {
    String serviceKey = properties.serviceKey();
    if (serviceKey.contains("%")) {
      return URLDecoder.decode(serviceKey, StandardCharsets.UTF_8);
    }
    return serviceKey;
  }

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }
}
