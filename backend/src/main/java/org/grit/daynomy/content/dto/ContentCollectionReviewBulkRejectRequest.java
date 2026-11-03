package org.grit.daynomy.content.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;

public record ContentCollectionReviewBulkRejectRequest(
    @NotEmpty @Size(max = 100) List<@Positive Long> contentIds) {}
