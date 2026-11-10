package org.grit.daynomy.content.dto;

public record ContentCollectionRematchResponse(
    int processedCount, int matchedCount, int suggestionCount) {}
