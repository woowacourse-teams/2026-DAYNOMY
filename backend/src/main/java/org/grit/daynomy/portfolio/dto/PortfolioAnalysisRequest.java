package org.grit.daynomy.portfolio.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PortfolioAnalysisRequest(
    @Schema(
            description =
                "분석할 자산 목록(최대 50개). 비회원은 빈 목록이면 AI 호출·체험 소진 없이 빈 분석을 반환합니다. 비어 있지 않으면 비중 합계는 100이어야 하며, 종목명은 대소문자를 무시하여 중복할 수 없습니다.")
        @NotNull(message = "포트폴리오 자산 목록은 필수입니다.")
        @Size(max = 50, message = "포트폴리오 자산은 최대 50개까지 등록할 수 있습니다.")
        List<@NotNull(message = "포트폴리오 자산은 null일 수 없습니다.") @Valid PortfolioAssetRequest> assets) {

  public PortfolioAnalysisRequest {
    assets = assets == null ? null : List.copyOf(assets);
  }
}
