package org.grit.daynomy.asset.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record AdminAssetIndustriesUpdateRequest(
    @NotNull @Size(max = 100) List<@Positive Long> industryIds) {}
