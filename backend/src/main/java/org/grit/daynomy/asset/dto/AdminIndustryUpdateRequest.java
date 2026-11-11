package org.grit.daynomy.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminIndustryUpdateRequest(
    @NotBlank @Size(max = 50) String name,
    @NotNull @PositiveOrZero Integer displayOrder,
    @NotNull Boolean active) {}
