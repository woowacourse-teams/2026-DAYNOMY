package org.grit.daynomy.investmentcalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventDirection;

public record InvestmentEventValueResponse(
    @Schema(description = "이전 발표값. 확인되지 않으면 null", nullable = true) BigDecimal previousValue,
    @Schema(description = "실제 발표값. 발표 전 또는 확인되지 않으면 null", nullable = true) BigDecimal actualValue,
    @Schema(description = "발표값 단위. 출처에서 제공하는 단위", example = "%") String unit,
    @Schema(
            description =
                "이전 값 대비 실제 값 변화: DECREASED(하락), UNCHANGED(동일), INCREASED(상승), UNAVAILABLE(이전·실제 값 중 하나 이상 없음)")
        InvestmentEventDirection direction) {

  public static InvestmentEventValueResponse from(InvestmentEvent event) {
    return new InvestmentEventValueResponse(
        event.getPreviousValue(), event.getActualValue(), event.getValueUnit(), event.direction());
  }
}
