package org.grit.daynomy.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record ContentCollectionManualRequest(
    @NotBlank @Size(max = 2000) String url,
    @Size(max = 255) String title,
    @Size(max = 10000) String description,
    LocalDate publishedDate) {}
