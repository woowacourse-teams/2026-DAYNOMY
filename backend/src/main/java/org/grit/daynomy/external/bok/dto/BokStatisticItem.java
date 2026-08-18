package org.grit.daynomy.external.bok.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record BokStatisticItem(
    @JsonProperty("STAT_CODE") String statisticCode,
    @JsonProperty("STAT_NAME") String statisticName,
    @JsonProperty("ITEM_CODE1") String itemCode,
    @JsonProperty("ITEM_NAME1") String itemName,
    @JsonProperty("UNIT_NAME") String unitName,
    @JsonProperty("TIME") String period,
    @JsonProperty("DATA_VALUE") String value) {}
