package org.grit.daynomy.content.dto;

public record ContentCollectionResultResponse(
    long sourceId, int fetchedCount, int savedCount, int duplicateCount, int linkedCount) {}
