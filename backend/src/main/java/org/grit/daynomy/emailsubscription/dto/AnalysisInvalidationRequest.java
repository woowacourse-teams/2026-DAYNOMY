package org.grit.daynomy.emailsubscription.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalysisInvalidationRequest(@NotBlank @Size(max = 500) String reason) {}
