package org.grit.daynomy.external.finlife;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StreamUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Component
public class FinlifeProductClient {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  private final FinlifeProperties properties;
  private final RestClient restClient;

  public FinlifeProductClient(FinlifeProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(properties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(properties.readTimeout()));
    this.restClient =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
  }

  public List<FinlifeProduct> getProducts() {
    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
      throw new BusinessException(ExternalErrorCode.FINLIFE_API_NOT_CONFIGURED);
    }

    List<FinlifeProduct> deposits =
        request(
            "/depositProductsSearch.json",
            "예금",
            ExternalErrorCode.FINLIFE_DEPOSIT_API_REQUEST_FAILED);
    List<FinlifeProduct> savings =
        request(
            "/savingProductsSearch.json",
            "적금",
            ExternalErrorCode.FINLIFE_SAVING_API_REQUEST_FAILED);
    List<FinlifeProduct> products = new ArrayList<>(deposits.size() + savings.size());
    products.addAll(deposits);
    products.addAll(savings);
    return List.copyOf(products);
  }

  private List<FinlifeProduct> request(
      String path, String type, ExternalErrorCode errorCode) {
    try {
      return restClient
          .get()
          .uri(
              builder ->
                  builder
                      .path(path)
                      .queryParam("auth", properties.apiKey())
                      .queryParam("topFinGrpNo", properties.bankGroupCode())
                      .queryParam("pageNo", 1)
                      .build())
          .exchange(
              (request, response) -> {
                String responseBody = readBody(response);
                log.info(
                    "Financial product API response: api={}, endpoint={}, baseUrl={}, status={}, contentType={}, bodyBytes={}",
                    type,
                    path,
                    properties.baseUrl(),
                    response.getStatusCode().value(),
                    response.getHeaders().getFirst("Content-Type"),
                    responseBody.getBytes(StandardCharsets.UTF_8).length);
                if (!response.getStatusCode().is2xxSuccessful()) {
                  throw new BusinessException(errorCode);
                }
                return parse(responseBody, type, errorCode);
              });
    } catch (BusinessException exception) {
      log.warn(
          "Financial product API returned an invalid response: api={}, endpoint={}, errorCode={}",
          type,
          path,
          errorCode.code());
      throw exception;
    } catch (RestClientResponseException exception) {
      log.warn(
          "Financial product API returned an HTTP error: api={}, endpoint={}, status={}, responseError={}",
          type,
          path,
          exception.getStatusCode().value(),
          responseError(exception.getResponseBodyAsString()));
      throw new BusinessException(errorCode);
    } catch (RestClientException exception) {
      log.warn(
          "Financial product API connection failed: api={}, endpoint={}, exception={}",
          type,
          path,
          exception.getClass().getSimpleName());
      throw new BusinessException(errorCode);
    } catch (RuntimeException exception) {
      log.warn(
          "Financial product API request failed: api={}, exception={}",
          type,
          exception.getClass().getSimpleName());
      throw new BusinessException(errorCode);
    }
  }

  private String readBody(ClientHttpResponse response) throws IOException {
    if (response.getBody() == null) {
      return "";
    }
    return StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8);
  }

  private List<FinlifeProduct> parse(
      String response, String type, ExternalErrorCode errorCode) {
    try {
      if (response == null || response.isBlank()) {
        log.warn(
            "Financial product API returned an empty response: api={}, reason=EMPTY_RESPONSE",
            type);
        throw new BusinessException(errorCode);
      }

      JsonNode root = OBJECT_MAPPER.readTree(response);
      if (root == null || !root.isObject()) {
        log.warn(
            "Financial product API returned invalid JSON: api={}, responseLength={}",
            type,
            response.length());
        throw new BusinessException(errorCode);
      }

      JsonNode result = root.path("result");
      if (!result.isObject()) {
        log.warn(
            "Financial product API response has no result object: api={}, responseLength={}",
            type,
            response.length());
        throw new BusinessException(errorCode);
      }

      String upstreamErrorCode = result.path("err_cd").asText("");
      if (!upstreamErrorCode.isBlank() && !"000".equals(upstreamErrorCode)) {
        log.warn(
            "Financial product API returned an upstream error: api={}, errorCode={}, errorMessage={}",
            type,
            upstreamErrorCode,
            result.path("err_msg").asText("unknown"));
        throw new BusinessException(errorCode);
      }

      Map<String, List<JsonNode>> optionsByProduct = new HashMap<>();
      for (JsonNode option : result.path("optionList")) {
        String key =
            productKey(option.path("fin_co_no").asText(), option.path("fin_prdt_cd").asText());
        optionsByProduct.computeIfAbsent(key, ignored -> new ArrayList<>()).add(option);
      }

      List<FinlifeProduct> products = new ArrayList<>();
      for (JsonNode base : result.path("baseList")) {
        String companyNumber = base.path("fin_co_no").asText();
        String productCode = base.path("fin_prdt_cd").asText();
        List<JsonNode> options =
            optionsByProduct.getOrDefault(productKey(companyNumber, productCode), List.of());
        for (JsonNode option : options) {
          BigDecimal baseRate = decimal(option.path("intr_rate"));
          BigDecimal maxRate = decimal(option.path("intr_rate2"));
          if (baseRate == null) {
            baseRate = maxRate;
          }
          if (maxRate == null) {
            maxRate = baseRate;
          }
          int termMonths = parseInt(option.path("save_trm").asText());
          if (baseRate == null || termMonths <= 0) {
            continue;
          }
          products.add(
              new FinlifeProduct(
                  companyNumber + "-" + productCode + "-" + termMonths,
                  base.path("kor_co_nm").asText("금융회사 미표기"),
                  base.path("fin_prdt_nm").asText("상품명 미표기"),
                  type,
                  baseRate,
                  maxRate,
                  termMonths,
                  parseLong(base.path("max_limit").asText()),
                  base.path("join_way").asText("-"),
                  base.path("spcl_cnd").asText("-"),
                  base.path("join_member").asText("-"),
                  base.path("join_deny").asText("-"),
                  base.path("dcls_month").asText("-")));
        }
      }
      return products;
    } catch (BusinessException exception) {
      throw exception;
    } catch (Exception exception) {
      throw new BusinessException(errorCode);
    }
  }

  private BigDecimal decimal(JsonNode value) {
    String text = value.asText("").trim();
    if (text.isBlank() || "-".equals(text)) return null;
    return new BigDecimal(text);
  }

  private int parseInt(String value) {
    try {
      return Integer.parseInt(value.trim());
    } catch (RuntimeException exception) {
      return 0;
    }
  }

  private long parseLong(String value) {
    try {
      return Long.parseLong(value.replace(",", "").trim());
    } catch (RuntimeException exception) {
      return 0;
    }
  }

  private String productKey(String companyNumber, String productCode) {
    return companyNumber + "|" + productCode;
  }

  private int toMillis(Duration duration) {
    return Math.toIntExact(duration.toMillis());
  }

  private String responseError(String responseBody) {
    try {
      JsonNode result = OBJECT_MAPPER.readTree(responseBody).path("result");
      String errorCode = result.path("err_cd").asText("");
      String errorMessage = result.path("err_msg").asText("");
      if (errorCode.isBlank() && errorMessage.isBlank()) {
        return "unstructured_response";
      }
      return errorCode + ":" + errorMessage;
    } catch (Exception exception) {
      return "unstructured_response";
    }
  }
}
