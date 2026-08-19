package org.grit.daynomy.external.kosis.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KosisDataItem(
    @JsonProperty("TBL_NM") String tableName,
    @JsonProperty("ITM_NM") String itemName,
    @JsonProperty("UNIT_NM") String unitName,
    @JsonProperty("PRD_DE") String period,
    @JsonProperty("DT") String value,
    @JsonProperty("LST_CHN_DE") String lastChangedDate) {}
