package org.grit.daynomy.emailsubscription.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

public record AnalysisInvalidationResponse(
    Long analysisId,
    @Schema(description = "최초 무효화 시각. 반복 요청 시 유지됩니다.") Instant invalidatedAt,
    @Schema(description = "이번 요청에서 취소된 대기·재시도 메일 수. 이미 접수·전송 중인 메일은 제외됩니다.")
        int cancelledDeliveries) {}
