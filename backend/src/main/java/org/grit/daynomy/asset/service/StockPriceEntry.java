package org.grit.daynomy.asset.service;

import java.math.BigDecimal;
import java.time.LocalDate;

public record StockPriceEntry(String assetCode, LocalDate baseDate, BigDecimal closePrice) {}
