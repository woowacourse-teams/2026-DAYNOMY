package org.grit.daynomy.asset.service;

public record UsStockSyncResult(
    StockSyncResult master, UsStockKoreanNameSyncResult koreanNames, String koreanNameErrorCode) {}
