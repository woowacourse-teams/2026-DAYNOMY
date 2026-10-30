package org.grit.daynomy.portfolio.dto;

public record PortfolioTransactionImportResponse(
    int importedCount, boolean alreadyImported, SavedPortfolioResponse portfolio) {}
