package org.grit.daynomy.content.dto;

public record ContentCollectionBatchResultResponse(
    int sourceCount,
    int successCount,
    int failureCount,
    int fetchedCount,
    int savedCount,
    int duplicateCount,
    int linkedCount) {}
