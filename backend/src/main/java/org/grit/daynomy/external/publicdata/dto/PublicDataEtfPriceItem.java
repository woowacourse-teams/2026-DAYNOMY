package org.grit.daynomy.external.publicdata.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicDataEtfPriceItem(
    String basDt, String srtnCd, String isinCd, String itmsNm, String clpr) {}
