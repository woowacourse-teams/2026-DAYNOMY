package org.grit.daynomy.external.dart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DartDisclosureItem(
    @JsonProperty("corp_cls") String corpCls, // 법인구분: Y(유가), K(코스닥), N(코넥스), E(기타)
    @JsonProperty("corp_name") String corpName, // 회사명
    @JsonProperty("corp_code") String corpCode, // DART 고유 회사 코드
    @JsonProperty("stock_code") String stockCode, // 종목 코드
    @JsonProperty("report_nm") String reportNm, // 공시명
    @JsonProperty("rcept_no") String rceptNo, // 접수 번호
    @JsonProperty("flr_nm") String flrNm, // 공시 제출인명
    @JsonProperty("rcept_dt") String rceptDt, // 접수일
    String rm // 비고
    ) {}
