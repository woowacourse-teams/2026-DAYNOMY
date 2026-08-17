package org.grit.daynomy.external.kosis;

import java.util.Arrays;
import java.util.List;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.ErrorCode;
import org.grit.daynomy.external.kosis.dto.KosisDataItem;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

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

      return response == null ? List.of() : Arrays.asList(response);
    } catch (RestClientException exception) {
      throw new BusinessException(ErrorCode.KOSIS_API_REQUEST_FAILED);
    }
  }
}
