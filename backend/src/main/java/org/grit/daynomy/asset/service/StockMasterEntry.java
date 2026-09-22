package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import org.grit.daynomy.asset.domain.StockMarket;

public record StockMasterEntry(
    String code, String name, StockMarket market, String isinCode, LocalDate baseDate) {}
