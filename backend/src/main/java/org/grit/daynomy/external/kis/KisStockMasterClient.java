package org.grit.daynomy.external.kis;

import java.util.List;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.grit.daynomy.external.RetryableExternalRequestException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KisStockMasterClient {
  public static final List<String> US_MARKETS = List.of("nas", "nys", "ams");
  private static final long MAX_ARCHIVE_BYTES = 10 * 1024 * 1024;
  private final RestClient restClient;

  public KisStockMasterClient(KisProperties properties) {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Math.toIntExact(properties.connectTimeout().toMillis()));
    factory.setReadTimeout(Math.toIntExact(properties.readTimeout().toMillis()));
    restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(factory).build();
  }

  public byte[] download(String market) {
    if (market == null || !US_MARKETS.contains(market)) {
      throw new IllegalArgumentException("지원하지 않는 미국 종목정보 시장입니다.");
    }
    try {
      return restClient
          .get()
          .uri("/{market}mst.cod.zip", market)
          .exchange(
              (request, response) -> {
                if (response.getStatusCode().is5xxServerError()
                    || response.getStatusCode().value() == 429) {
                  throw new RetryableExternalRequestException(
                      ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED);
                }
                if (!response.getStatusCode().is2xxSuccessful()
                    || response.getHeaders().getContentLength() > MAX_ARCHIVE_BYTES) {
                  throw invalidMaster();
                }
                byte[] body = response.getBody().readNBytes(Math.toIntExact(MAX_ARCHIVE_BYTES + 1));
                if (body.length > MAX_ARCHIVE_BYTES) throw invalidMaster();
                return body;
              });
    } catch (ResourceAccessException exception) {
      throw new RetryableExternalRequestException(
          ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED);
    } catch (RestClientException exception) {
      throw invalidMaster();
    }
  }

  private BusinessException invalidMaster() {
    return new BusinessException(ExternalErrorCode.KIS_STOCK_MASTER_REQUEST_FAILED);
  }
}
