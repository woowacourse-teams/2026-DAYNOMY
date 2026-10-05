package org.grit.daynomy.investmentcalendar.dto;

import org.grit.daynomy.investmentcalendar.domain.InvestmentEvent;

public record InvestmentEventSourceResponse(String name, String url) {

  public static InvestmentEventSourceResponse from(InvestmentEvent event) {
    return new InvestmentEventSourceResponse(event.getSourceName(), event.getSourceUrl());
  }
}
