package org.grit.daynomy.external.dart.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DartMergerDecisionItem(
    @JsonProperty("rcept_no") String rceptNo,
    @JsonProperty("corp_cls") String corpCls,
    @JsonProperty("corp_code") String corpCode,
    @JsonProperty("corp_name") String corpName,
    @JsonProperty("mg_mth") String mergerMethod, // 합병방법
    @JsonProperty("mg_stn") String mergerType, // 합병형태
    @JsonProperty("mg_pp") String mergerPurpose, // 합병목적
    @JsonProperty("mg_rt") String mergerRatio, // 합병비율
    @JsonProperty("mg_rt_bs") String mergerRatioBasis, // 합병비율 산출근거
    @JsonProperty("mgptncmp_cmpnm") String counterpartyCompanyName, // 합병상대회사명
    @JsonProperty("mgptncmp_mbsn") String counterpartyMainBusiness, // 합병상대회사 주요사업
    @JsonProperty("mgsc_mgctrd") String mergerContractDate, // 합병계약일
    @JsonProperty("mgsc_gmtsck_prd") String shareholderMeetingDate, // 주주총회예정일자
    @JsonProperty("mgsc_mgdt") String mergerDate, // 합병기일
    @JsonProperty("bddd") String boardDecisionDate // 이사회결의일
    ) {}
