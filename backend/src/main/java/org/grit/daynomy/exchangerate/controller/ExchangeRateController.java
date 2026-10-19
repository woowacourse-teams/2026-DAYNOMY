package org.grit.daynomy.exchangerate.controller;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.exchangerate.service.ExchangeRateService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/exchange-rates")
public class ExchangeRateController {
  private final ExchangeRateService service;

  @GetMapping("/usd-krw")
  public org.grit.daynomy.exchangerate.dto.ExchangeRateResponse get(
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    return service.quote(date);
  }
}
