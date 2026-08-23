package org.grit.daynomy.portfolio.ai;

public record PortfolioAnalysisTarget(
    Long assetId, Long bookmarkId, String name, String category, String assetCode) {}
