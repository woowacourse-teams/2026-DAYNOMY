package org.grit.daynomy.external.kosis;

import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.kosis.dto.KosisDataItem;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class KosisClient {

  private final KosisProperties kosisProperties;
  private final RestClient restClient;

  public KosisClient(KosisProperties kosisProperties) {
    this.kosisProperties = kosisProperties;
    this.restClient = RestClient.create(kosisProperties.baseUrl());
  }

  public List<KosisDataItem> getRecentData(KosisProperties.Indicator indicator) {
    try {
      log.info(
          "Requesting KOSIS recent data: key={}, name={}, period={}, tableName={}",
          indicator.key(),
          indicator.name(),
          indicator.period(),
          indicator.tableName());
      KosisDataItem[] response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/statisticsData.do")
                          .queryParam("method", "getList")
                          .queryParam("apiKey", kosisProperties.apiKey())
                          .queryParam("format", "json")
                          .queryParam("jsonVD", "Y")
                          .queryParam("userStatsId", indicator.userStatsId())
                          .queryParam("prdSe", indicator.period())
                          .queryParam("newEstPrdCnt", 2)
                          .build())
              .retrieve()
              .body(KosisDataItem[].class);

      List<KosisDataItem> items = response == null ? List.of() : Arrays.asList(response);
      log.info(
          "Received KOSIS recent data: key={}, responseCount={}", indicator.key(), items.size());
      return items;
    } catch (RestClientException exception) {
      log.warn(
          "KOSIS recent data request failed: key={}, name={}, period={}, message={}",
          indicator.key(),
          indicator.name(),
          indicator.period(),
          exception.getMessage());
      throw new BusinessException(ExternalErrorCode.KOSIS_API_REQUEST_FAILED);
    }
  }
}
