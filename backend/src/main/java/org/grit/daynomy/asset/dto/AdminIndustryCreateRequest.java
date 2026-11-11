package org.grit.daynomy.asset.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminIndustryCreateRequest(
    @NotBlank @Size(max = 50) @Pattern(regexp = "[A-Z][A-Z0-9_]*") String code,
    @NotBlank @Size(max = 50) String name,
    @NotNull @PositiveOrZero Integer displayOrder) {}
