package org.grit.daynomy.external.publicdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicDataStockPriceItem(
    String basDt, String srtnCd, String itmsNm, String mrktCtg, String clpr, String mrktTotAmt) {}
