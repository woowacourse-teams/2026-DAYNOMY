package org.grit.daynomy.external.dart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DartConvertibleBondItem(
    @JsonProperty("rcept_no") String rceptNo,
    @JsonProperty("corp_cls") String corpCls,
    @JsonProperty("corp_code") String corpCode,
    @JsonProperty("corp_name") String corpName,
    @JsonProperty("bd_fta") String bondTotalAmount, // 사채의 권면총액
    @JsonProperty("fdpp_fclt") String facilityFund, // 자금조달 목적(시설자금)
    @JsonProperty("fdpp_op") String operatingFund, // 자금조달 목적(운영자금)
    @JsonProperty("fdpp_dtrp") String debtRepaymentFund, // 자금조달 목적(채무상환자금)
    @JsonProperty("bd_intr_ex") String surfaceInterestRate, // 표면이자율
    @JsonProperty("bd_intr_sf") String maturityInterestRate, // 만기이자율
    @JsonProperty("bd_mtd") String maturityDate, // 사채만기일
    @JsonProperty("cv_rt") String conversionRate, // 전환비율
    @JsonProperty("cv_prc") String conversionPrice, // 전환가액
    @JsonProperty("cvisstk_cnt") String conversionStockCount, // 전환에 따라 발행할 주식수
    @JsonProperty("cvisstk_tisstk_vs") String conversionStockRatio, // 주식총수 대비 비율
    @JsonProperty("cvrqpd_bgd") String conversionRequestStartDate, // 전환청구기간 시작일
    @JsonProperty("cvrqpd_edd") String conversionRequestEndDate, // 전환청구기간 종료일
    @JsonProperty("bddd") String boardDecisionDate // 이사회결의일
    ) {}
