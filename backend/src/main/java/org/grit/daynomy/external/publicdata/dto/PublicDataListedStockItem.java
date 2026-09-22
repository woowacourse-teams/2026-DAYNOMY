package org.grit.daynomy.external.publicdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicDataListedStockItem(
    String basDt,
    String srtnCd,
    String isinCd,
    String mrktCtg,
    String itmsNm,
    String crno,
    String corpNm) {}
