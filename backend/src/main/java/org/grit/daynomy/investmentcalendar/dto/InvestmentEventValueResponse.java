package org.grit.daynomy.investmentcalendar.dto;

import java.math.BigDecimal;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEventDirection;

public record InvestmentEventValueResponse(
    BigDecimal previousValue,
    BigDecimal actualValue,
    String unit,
    InvestmentEventDirection direction) {

  public static InvestmentEventValueResponse from(InvestmentEvent event) {
    return new InvestmentEventValueResponse(
        event.getPreviousValue(), event.getActualValue(), event.getValueUnit(), event.direction());
  }
}
