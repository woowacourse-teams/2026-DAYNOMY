package org.grit.daynomy.content.dto;

import java.time.Instant;
import org.grit.daynomy.content.domain.ContentType;

public record ContentPopularityItemResponse(
    ContentType contentType, Long contentId, long score, Instant lastInteractedAt) {}
