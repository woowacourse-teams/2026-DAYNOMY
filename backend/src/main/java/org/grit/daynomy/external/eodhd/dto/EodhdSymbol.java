package org.grit.daynomy.external.eodhd.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record EodhdSymbol(
    @JsonProperty("Code") String code,
    @JsonProperty("Name") String name,
    @JsonProperty("Exchange") String exchange,
    @JsonProperty("Currency") String currency,
    @JsonProperty("Type") String type,
    @JsonProperty("Isin") String isin) {}
