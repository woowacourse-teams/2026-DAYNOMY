package org.grit.daynomy.external.bok;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.bok.dto.BokStatisticItem;
import org.grit.daynomy.external.bok.dto.BokStatisticResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class BokClient {

  private static final String DAILY = "D";
  private static final String MONTHLY = "M";
  private static final String YEARLY = "Y";
  private static final String NO_DATA_CODE = "INFO-200";
  private static final DateTimeFormatter DAILY_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
  private static final DateTimeFormatter MONTHLY_FORMAT = DateTimeFormatter.ofPattern("yyyyMM");

  private final BokProperties bokProperties;
  private final RestClient restClient;

  public BokClient(BokProperties bokProperties) {
    this.bokProperties = bokProperties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(bokProperties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(bokProperties.readTimeout()));
    this.restClient =
        RestClient.builder()
            .baseUrl(bokProperties.baseUrl())
            .requestFactory(requestFactory)
            .build();
  }

  public List<BokStatisticItem> getRecentData(BokProperties.Indicator indicator) {
    LocalDate today = LocalDate.now();
    try {
      BokStatisticResponse response =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path(
                              apiPath(
                                  indicator,
                                  startPeriod(indicator.cycle(), today),
                                  endPeriod(indicator.cycle(), today)))
                          .build())
              .retrieve()
              .body(BokStatisticResponse.class);

      if (response == null || isNoDataResult(response.result())) {
        return List.of();
      }
      if (response.result() != null) {
        log.warn(
            "BOK API returned error result: code={}, message={}",
            response.result().code(),
            response.result().message());
        throw new BusinessException(ExternalErrorCode.BOK_API_REQUEST_FAILED);
      }
      if (response.search() == null || response.search().rows() == null) {
        return List.of();
      }
      return response.search().rows();
    } catch (RestClientException exception) {
      throw new BusinessException(ExternalErrorCode.BOK_API_REQUEST_FAILED);
    }
  }

  private boolean isNoDataResult(BokStatisticResponse.Result result) {
    return result != null && NO_DATA_CODE.equals(result.code());
  }

  private String apiPath(BokProperties.Indicator indicator, String startPeriod, String endPeriod) {
    List<String> paths = new ArrayList<>();
    paths.add("");
    paths.add("api");
    paths.add("StatisticSearch");
    paths.add(bokProperties.apiKey());
    paths.add("json");
    paths.add("kr");
    paths.add("1");
    paths.add("100");
    paths.add(indicator.statisticCode());
    paths.add(indicator.cycle());
    paths.add(startPeriod);
    paths.add(endPeriod);
    paths.addAll(
        indicator.itemCodes() == null
            ? List.of()
            : indicator.itemCodes().stream().filter(itemCode -> !itemCode.isBlank()).toList());
    return String.join("/", paths);
  }

  private String startPeriod(String cycle, LocalDate today) {
    if (DAILY.equals(cycle)) {
      return today.minusDays(14).format(DAILY_FORMAT);
    }
    if (MONTHLY.equals(cycle)) {
      return today.minusMonths(3).format(MONTHLY_FORMAT);
    }
    if (YEARLY.equals(cycle)) {
      return String.valueOf(today.minusYears(2).getYear());
    }
    return today.minusMonths(3).format(MONTHLY_FORMAT);
  }

  private String endPeriod(String cycle, LocalDate today) {
    if (DAILY.equals(cycle)) {
      return today.format(DAILY_FORMAT);
    }
    if (MONTHLY.equals(cycle)) {
      return today.format(MONTHLY_FORMAT);
    }
    if (YEARLY.equals(cycle)) {
      return String.valueOf(today.getYear());
    }
    return today.format(MONTHLY_FORMAT);
  }

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }
}
