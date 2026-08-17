package org.grit.daynomy.external.dart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DartCapitalIncreaseItem(
    @JsonProperty("rcept_no") String rceptNo,
    @JsonProperty("corp_cls") String corpCls,
    @JsonProperty("corp_code") String corpCode,
    @JsonProperty("corp_name") String corpName,
    @JsonProperty("nstk_ostk_cnt") String newCommonStockCount, // 신주의 종류와 수(보통주식)
    @JsonProperty("bfic_tisstk_ostk") String beforeCommonStockCount, // 증자 전 발행주식총수(보통주식)
    @JsonProperty("fdpp_fclt") String facilityFund, // 자금조달 목적(시설자금)
    @JsonProperty("fdpp_op") String operatingFund, // 자금조달 목적(운영자금)
    @JsonProperty("fdpp_dtrp") String debtRepaymentFund, // 자금조달 목적(채무상환자금)
    @JsonProperty("fdpp_ocsa") String otherCompanySecuritiesFund, // 자금조달 목적(타법인 증권 취득자금)
    @JsonProperty("fdpp_etc") String otherFund, // 자금조달 목적(기타자금)
    @JsonProperty("ic_mthn") String increaseMethod // 증자방식
    ) {}
