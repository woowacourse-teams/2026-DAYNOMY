package org.grit.daynomy.external.dart;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.zip.ZipInputStream;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.dart.dto.DartCapitalIncreaseResponse;
import org.grit.daynomy.external.dart.dto.DartConvertibleBondResponse;
import org.grit.daynomy.external.dart.dto.DartDisclosureResponse;
import org.grit.daynomy.external.dart.dto.DartMergerDecisionResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(dartProperties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(dartProperties.readTimeout()));
    this.restClient =
        RestClient.builder()
            .baseUrl(dartProperties.baseUrl())
            .requestFactory(requestFactory)
            .build();
  }

  public DartDisclosureResponse getDisclosures(
      LocalDate beginDate, LocalDate endDate, String disclosureType, String corporationClass) {
    try {
      DartDisclosureResponse response =
          restClient
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
                          .queryParam("page_count", 20)
                          .build())
              .retrieve()
              .body(DartDisclosureResponse.class);
      validateStatus(response == null ? null : response.status());
      return response;
    } catch (RestClientException exception) {
      throw new BusinessException(ExternalErrorCode.DART_API_REQUEST_FAILED);
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

  public String getOriginalDocument(String receiptNo) {
    try {
      byte[] document =
          restClient
              .get()
              .uri(
                  uriBuilder ->
                      uriBuilder
                          .path("/document.xml")
                          .queryParam("crtfc_key", dartProperties.apiKey())
                          .queryParam("rcept_no", receiptNo)
                          .build())
              .retrieve()
              .body(byte[].class);
      if (document == null || document.length == 0) {
        throw new BusinessException(ExternalErrorCode.DART_API_REQUEST_FAILED);
      }
      try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(document))) {
        StringBuilder content = new StringBuilder();
        var entry = zip.getNextEntry();
        while (entry != null) {
          if (!entry.isDirectory()) {
            if (content.length() > 0) {
              content.append("\n\n");
            }
            content.append("[DART 원문 파일: ").append(entry.getName()).append("]\n");
            content.append(new String(zip.readAllBytes(), StandardCharsets.UTF_8));
          }
          entry = zip.getNextEntry();
        }
        if (content.isEmpty()) {
          throw new BusinessException(ExternalErrorCode.DART_API_REQUEST_FAILED);
        }
        return content.toString();
      }
    } catch (RestClientException | IOException exception) {
      throw new BusinessException(ExternalErrorCode.DART_API_REQUEST_FAILED);
    }
  }

  private <T> T getMajorReport(
      String path,
      Class<T> responseType,
      String corporationCode,
      LocalDate beginDate,
      LocalDate endDate) {
    try {
      T response =
          restClient
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
      validateStatus(statusOf(response));
      return response;
    } catch (RestClientException exception) {
      throw new BusinessException(ExternalErrorCode.DART_API_REQUEST_FAILED);
    }
  }

  private void validateStatus(String status) {
    if (!"000".equals(status) && !"013".equals(status)) {
      throw new BusinessException(ExternalErrorCode.DART_API_REQUEST_FAILED);
    }
  }

  private String statusOf(Object response) {
    if (response instanceof DartCapitalIncreaseResponse capitalIncreaseResponse) {
      return capitalIncreaseResponse.status();
    }
    if (response instanceof DartConvertibleBondResponse convertibleBondResponse) {
      return convertibleBondResponse.status();
    }
    if (response instanceof DartMergerDecisionResponse mergerDecisionResponse) {
      return mergerDecisionResponse.status();
    }
    return null;
  }

  private String formatDate(LocalDate date) {
    return date.format(DART_DATE_FORMAT);
  }

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }
}
