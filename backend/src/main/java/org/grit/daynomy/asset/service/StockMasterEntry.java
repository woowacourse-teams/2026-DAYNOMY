package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;

public record StockMasterEntry(
    String code,
    String name,
    AssetCategory category,
    StockMarket market,
    String isinCode,
    LocalDate baseDate) {}
