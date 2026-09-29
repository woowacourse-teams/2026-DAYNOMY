package org.grit.daynomy.asset.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.grit.daynomy.asset.domain.AssetCategory;

public record StockPriceEntry(
    String assetCode, AssetCategory category, LocalDate baseDate, BigDecimal closePrice) {}
