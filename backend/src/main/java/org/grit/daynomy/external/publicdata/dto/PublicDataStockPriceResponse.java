package org.grit.daynomy.external.publicdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicDataStockPriceResponse(Response response) {

  public Header header() {
    return response == null ? null : response.header();
  }

  public Body body() {
    return response == null ? null : response.body();
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Response(Header header, Body body) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Header(String resultCode, String resultMsg) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Body(int numOfRows, int pageNo, int totalCount, Items items) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Items(List<PublicDataStockPriceItem> item) {}
}
