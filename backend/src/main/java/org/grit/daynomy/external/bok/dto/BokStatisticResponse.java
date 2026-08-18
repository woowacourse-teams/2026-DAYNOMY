package org.grit.daynomy.external.bok.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record BokStatisticResponse(@JsonProperty("StatisticSearch") StatisticSearch search) {

  public record StatisticSearch(@JsonProperty("row") List<BokStatisticItem> rows) {}
}
