package org.grit.daynomy.external.publicdata.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class PublicDataStockPriceResponseTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void parsesWrappedPublicDataStockPriceResponse() throws Exception {
    String json =
        """
        {
          "response": {
            "header": {
              "resultCode": "00",
              "resultMsg": "NORMAL SERVICE."
            },
            "body": {
              "numOfRows": 1,
              "pageNo": 1,
              "totalCount": 1,
              "items": {
                "item": [
                  {
                    "basDt": "20260903",
                    "srtnCd": "000001",
                    "itmsNm": "테스트종목",
                    "mrktCtg": "KOSDAQ",
                    "clpr": "1000",
                    "mrktTotAmt": "300000"
                  }
                ]
              }
            }
          }
        }
        """;

    PublicDataStockPriceResponse response =
        objectMapper.readValue(json, PublicDataStockPriceResponse.class);

    assertThat(response.header().resultCode()).isEqualTo("00");
    assertThat(response.body().totalCount()).isEqualTo(1);
    assertThat(response.body().items().item())
        .singleElement()
        .satisfies(
            item -> {
              assertThat(item.basDt()).isEqualTo("20260903");
              assertThat(item.srtnCd()).isEqualTo("000001");
              assertThat(item.mrktCtg()).isEqualTo("KOSDAQ");
              assertThat(item.mrktTotAmt()).isEqualTo("300000");
            });
  }
}
