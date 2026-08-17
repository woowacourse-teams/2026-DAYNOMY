package org.grit.daynomy.external.dart;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.grit.daynomy.common.BusinessException;
import org.grit.daynomy.common.ErrorCode;
import org.grit.daynomy.external.dart.dto.DartCapitalIncreaseResponse;
import org.grit.daynomy.external.dart.dto.DartConvertibleBondResponse;
import org.grit.daynomy.external.dart.dto.DartDisclosureResponse;
import org.grit.daynomy.external.dart.dto.DartMergerDecisionResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DartClient {

  private static final DateTimeFormatter DART_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

  private final DartProperties dartProperties;
  private final RestClient restClient;

  public DartClient(DartProperties dartProperties) {
    this.dartProperties = dartProperties;
    this.restClient = RestClient.create(dartProperties.baseUrl());
  }

  public DartDisclosureResponse getDisclosures(
      LocalDate beginDate, LocalDate endDate, String disclosureType, String corporationClass) {
    try {
      return restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path("/list.json")
                      .queryParam("crtfc_key", dartProperties.apiKey())
                      .queryParam("bgn_de", formatDate(beginDate))
                      .queryParam("end_de", formatDate(endDate))
                      .queryParam("pblntf_ty", disclosureType)
                      .queryParam("corp_cls", corporationClass)
                      .queryParam("sort", "date")
                      .queryParam("sort_mth", "desc")
                      .queryParam("page_no", 1)
                      .queryParam("page_count", 100)
                      .build())
          .retrieve()
          .body(DartDisclosureResponse.class);
    } catch (RestClientException exception) {
      throw new BusinessException(ErrorCode.DART_API_REQUEST_FAILED);
    }
  }

  public DartCapitalIncreaseResponse getCapitalIncreases(
      String corporationCode, LocalDate beginDate, LocalDate endDate) {
    return getMajorReport(
        "/piicDecsn.json", DartCapitalIncreaseResponse.class, corporationCode, beginDate, endDate);
  }

  public DartConvertibleBondResponse getConvertibleBonds(
      String corporationCode, LocalDate beginDate, LocalDate endDate) {
    return getMajorReport(
        "/cvbdIsDecsn.json",
        DartConvertibleBondResponse.class,
        corporationCode,
        beginDate,
        endDate);
  }

  public DartMergerDecisionResponse getMergerDecisions(
      String corporationCode, LocalDate beginDate, LocalDate endDate) {
    return getMajorReport(
        "/cmpMgDecsn.json", DartMergerDecisionResponse.class, corporationCode, beginDate, endDate);
  }

  private <T> T getMajorReport(
      String path,
      Class<T> responseType,
      String corporationCode,
      LocalDate beginDate,
      LocalDate endDate) {
    try {
      return restClient
          .get()
          .uri(
              uriBuilder ->
                  uriBuilder
                      .path(path)
                      .queryParam("crtfc_key", dartProperties.apiKey())
                      .queryParam("corp_code", corporationCode)
                      .queryParam("bgn_de", formatDate(beginDate))
                      .queryParam("end_de", formatDate(endDate))
                      .build())
          .retrieve()
          .body(responseType);
    } catch (RestClientException exception) {
      throw new BusinessException(ErrorCode.DART_API_REQUEST_FAILED);
    }
  }

  private String formatDate(LocalDate date) {
    return date.format(DART_DATE_FORMAT);
  }
}
