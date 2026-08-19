package org.grit.daynomy.external.kosis;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
  private final ObjectMapper objectMapper = new ObjectMapper();

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
      String response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/statisticsData.do")
                          .queryParam("method", "getList")
                          .queryParam("apiKey", kosisProperties.apiKey())
                          .queryParam("format", "json")
                          .queryParam("content", "json")
                          .queryParam("jsonVD", "Y")
                          .queryParam("userStatsId", indicator.userStatsId())
                          .queryParam("prdSe", indicator.period())
                          .queryParam("newEstPrdCnt", 2)
                          .build())
              .retrieve()
              .body(String.class);

      List<KosisDataItem> items = parseResponse(indicator, response);
      log.info(
          "Received KOSIS recent data: key={}, responseCount={}", indicator.key(), items.size());
      return items;
    } catch (RestClientException | JsonProcessingException exception) {
      log.warn(
          "KOSIS recent data request failed: key={}, name={}, period={}, message={}",
          indicator.key(),
          indicator.name(),
          indicator.period(),
          exception.getMessage());
      throw new BusinessException(ExternalErrorCode.KOSIS_API_REQUEST_FAILED);
    }
  }

  private List<KosisDataItem> parseResponse(KosisProperties.Indicator indicator, String response)
          throws JsonProcessingException {
    if (response == null || response.isBlank()) {
      return List.of();
    }

    String trimmedResponse = response.stripLeading();
    if (!trimmedResponse.startsWith("[")) {
      log.warn(
              "KOSIS returned non-array response: key={}, responsePreview={}",
              indicator.key(),
              preview(trimmedResponse));
      throw new JsonProcessingException("KOSIS response is not a JSON array") {};
    }

    return Arrays.asList(objectMapper.readValue(trimmedResponse, KosisDataItem[].class));
  }

  private String preview(String response) {
    return response.substring(0, Math.min(response.length(), 300)).replaceAll("\\s+", " ");
  }
}
