package org.grit.daynomy.pmfsurvey.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.grit.daynomy.pmfsurvey.domain.PmfAnswer;

public record PmfSubmissionRequest(@NotNull PmfAnswer answer, @Size(max = 500) String reason) {

  public PmfSubmissionRequest {
    if (reason != null) {
      reason = reason.strip();
      if (reason.isEmpty()) {
        reason = null;
      }
    }
  }
}
