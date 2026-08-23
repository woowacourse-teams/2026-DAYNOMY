package org.grit.daynomy.external.publicdata;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.publicdata.dto.PublicDataStockPriceResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class PublicDataStockPriceClient {

  private static final DateTimeFormatter BASIC_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private static final String KOSDAQ = "KOSDAQ";

  private final PublicDataProperties publicDataProperties;
  private final RestClient restClient;

  public PublicDataStockPriceClient(PublicDataProperties publicDataProperties) {
    this.publicDataProperties = publicDataProperties;
    this.restClient = RestClient.create(publicDataProperties.stockPriceUrl());
  }

  public PublicDataStockPriceResponse getKosdaqStockPrices(LocalDate baseDate) {
    try {
      PublicDataStockPriceResponse response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .queryParam("serviceKey", publicDataProperties.serviceKey())
                          .queryParam("numOfRows", 3000)
                          .queryParam("pageNo", 1)
                          .queryParam("resultType", "json")
                          .queryParam("basDt", baseDate.format(BASIC_DATE_FORMAT))
                          .queryParam("mrktCls", KOSDAQ)
                          .build(true))
              .retrieve()
              .body(PublicDataStockPriceResponse.class);
      validateResponse(response);
      return response;
    } catch (RestClientException exception) {
      log.warn(
          "Public data stock price request failed: baseDate={}, message={}",
          baseDate,
          exception.getMessage());
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
  }

  private void validateResponse(PublicDataStockPriceResponse response) {
    if (response == null || response.header() == null || response.body() == null) {
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
    if (!"00".equals(response.header().resultCode())) {
      log.warn(
          "Public data stock price response failed: code={}, message={}",
          response.header().resultCode(),
          response.header().resultMsg());
      throw new BusinessException(ExternalErrorCode.PUBLIC_DATA_API_REQUEST_FAILED);
    }
  }
}
