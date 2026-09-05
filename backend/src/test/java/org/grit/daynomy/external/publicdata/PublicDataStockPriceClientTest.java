package org.grit.daynomy.external.publicdata;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PublicDataStockPriceClientTest {

  @Test
  void decodesEncodedServiceKeyBeforeBuildingQueryParam() {
    PublicDataStockPriceClient client =
        new PublicDataStockPriceClient(
            new PublicDataProperties("abc%2Fdef%2Bghi%3D%3D", "https://example.com"));

    assertThat(client.normalizedServiceKey()).isEqualTo("abc/def+ghi==");
  }

  @Test
  void keepsRawServiceKeyAsIs() {
    PublicDataStockPriceClient client =
        new PublicDataStockPriceClient(
            new PublicDataProperties("abc/def+ghi==", "https://example.com"));

    assertThat(client.normalizedServiceKey()).isEqualTo("abc/def+ghi==");
  }
}
