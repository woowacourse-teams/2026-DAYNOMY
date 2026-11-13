package org.grit.daynomy.content.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record ContentCollectionReviewApproveRequest(
    @Size(max = 10) List<@NotNull @Positive Long> assetIds,
    @Size(max = 100) List<@NotNull @Positive Long> industryIds,
    @PastOrPresent LocalDate publishedDate) {

  public ContentCollectionReviewApproveRequest {
    assetIds = assetIds == null ? List.of() : assetIds;
    industryIds = industryIds == null ? List.of() : industryIds;
  }

  @AssertTrue(message = "종목 또는 업종을 하나 이상 선택해야 합니다.")
  public boolean isTargetSelected() {
    return !assetIds.isEmpty() || !industryIds.isEmpty();
  }
}
