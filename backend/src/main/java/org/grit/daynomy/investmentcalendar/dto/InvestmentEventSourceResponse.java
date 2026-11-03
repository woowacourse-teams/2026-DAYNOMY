package org.grit.daynomy.investmentcalendar.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;

public record InvestmentEventSourceResponse(
    @Schema(description = "출처 기관명") String name, @Schema(description = "출처 URL") String url) {

  public static InvestmentEventSourceResponse from(InvestmentEvent event) {
    return new InvestmentEventSourceResponse(event.getSourceName(), event.getSourceUrl());
  }
}
