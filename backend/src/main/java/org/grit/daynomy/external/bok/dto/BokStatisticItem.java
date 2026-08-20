package org.grit.daynomy.external.bok.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BokStatisticItem(
    @JsonProperty("STAT_CODE") String statisticCode, // 통계표 코드
    @JsonProperty("STAT_NAME") String statisticName, // 통계표명
    @JsonProperty("ITEM_CODE1") String itemCode, // 통계 항목 코드
    @JsonProperty("ITEM_NAME1") String itemName, // 통계 항목명
    @JsonProperty("UNIT_NAME") String unitName, // 단위
    @JsonProperty("TIME") String period, // 기준 시점
    @JsonProperty("DATA_VALUE") String value // 통계값
    ) {}
