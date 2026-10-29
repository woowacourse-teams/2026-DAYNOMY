package org.grit.daynomy.content.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ContentCollectionReviewApproveRequest(
    @NotEmpty @Size(max = 10) List<@Positive Long> assetIds,
    @PastOrPresent LocalDate publishedDate) {}
