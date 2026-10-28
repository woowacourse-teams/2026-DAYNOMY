package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;

public record PortfolioTransactionResponse(
    @Schema(description = "등록·수정·삭제한 거래의 이력 ID", example = "1") Long transactionId,
    @Schema(
            description = "거래 반영 후 해당 종목의 현재 보유자산. 전량 매도 등으로 남은 보유자산이 없으면 null",
            types = {"object", "null"},
            schemaResolution = Schema.SchemaResolution.INLINE)
        SavedPortfolioHoldingResponse holding,
    @Schema(description = "변경된 거래 원장 기준 포트폴리오 전체 누적 실현손익(KRW)", example = "0.00")
        BigDecimal totalRealizedProfitLoss) {}
