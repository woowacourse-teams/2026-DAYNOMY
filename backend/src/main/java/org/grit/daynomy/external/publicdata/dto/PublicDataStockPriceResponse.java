package org.grit.daynomy.external.publicdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicDataStockPriceResponse(Header header, Body body) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Header(String resultCode, String resultMsg) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Body(int numOfRows, int pageNo, int totalCount, Items items) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Items(List<PublicDataStockPriceItem> item) {}
}
