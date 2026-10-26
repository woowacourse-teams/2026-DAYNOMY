package org.grit.daynomy.external.eodhd.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EodhdDailyPrice(String code, String exchange, LocalDate date, BigDecimal close) {}
