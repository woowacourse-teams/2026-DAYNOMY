package org.grit.daynomy.external.publicdata;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PublicDataStockPriceClient {

  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String KOSDAQ = "KOSDAQ";

  private final PublicDataProperties publicDataProperties;
  private final RestClient restClient;

  public PublicDataStockPriceClient(PublicDataProperties publicDataProperties) {
    this.publicDataProperties = publicDataProperties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(publicDataProperties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(publicDataProperties.readTimeout()));
    this.restClient =
        RestClient.builder()
            .baseUrl(publicDataProperties.stockPriceUrl())
            .requestFactory(requestFactory)
            .build();
  }

  public PublicDataStockPriceResponse getKosdaqStockPrices(LocalDate baseDate) {
    try {
      PublicDataStockPriceResponse response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .queryParam("serviceKey", normalizedServiceKey())
                          .queryParam("numOfRows", 3000)
                          .queryParam("pageNo", 1)
                          .queryParam("resultType", "json")
                          .queryParam("basDt", baseDate.format(BASIC_DATE_FORMAT))
                          .queryParam("mrktCls", KOSDAQ)
                          .build())
              .retrieve()
              .body(PublicDataStockPriceResponse.class);
      validateResponse(response);
      return response;
    } catch (RestClientException exception) {
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
  }

  private void validateResponse(PublicDataStockPriceResponse response) {
    if (response == null || response.header() == null || response.body() == null) {
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
    if (!"00".equals(response.header().resultCode())) {
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
  }

  String normalizedServiceKey() {
    String serviceKey = publicDataProperties.serviceKey();
    if (serviceKey.contains("%")) {
      return URLDecoder.decode(serviceKey, StandardCharsets.UTF_8);
    }
    return serviceKey;
  }

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }
}
