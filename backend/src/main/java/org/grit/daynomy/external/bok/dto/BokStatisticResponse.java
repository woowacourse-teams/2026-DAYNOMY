package org.grit.daynomy.external.bok.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BokStatisticResponse(@JsonProperty("StatisticSearch") StatisticSearch search) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record StatisticSearch(@JsonProperty("row") List<BokStatisticItem> rows) {}
}
