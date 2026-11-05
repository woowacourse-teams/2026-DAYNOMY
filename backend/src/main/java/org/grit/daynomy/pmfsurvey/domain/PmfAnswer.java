package org.grit.daynomy.pmfsurvey.domain;

import com.fasterxml.jackson.annotation.JsonCreator;

public enum PmfAnswer {
  VERY_DISAPPOINTED,
  SOMEWHAT_DISAPPOINTED,
  NOT_DISAPPOINTED,
  NO_LONGER_USING;

  @JsonCreator
  public static PmfAnswer fromCode(String code) {
    return valueOf(code);
  }
}
