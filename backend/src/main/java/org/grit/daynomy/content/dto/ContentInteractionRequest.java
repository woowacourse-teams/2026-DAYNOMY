package org.grit.daynomy.content.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.grit.daynomy.content.domain.ContentInteractionType;
import org.grit.daynomy.content.domain.ContentType;

public record ContentInteractionRequest(
    @NotNull UUID eventId,
    @NotNull ContentType contentType,
    @NotNull @Positive Long contentId,
    @NotNull ContentInteractionType interactionType) {}
