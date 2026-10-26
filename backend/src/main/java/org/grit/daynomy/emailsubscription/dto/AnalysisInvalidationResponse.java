package org.grit.daynomy.emailsubscription.dto;

import java.time.Instant;

public record AnalysisInvalidationResponse(
    Long analysisId, Instant invalidatedAt, int cancelledDeliveries) {}
