package org.grit.daynomy.common.response;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public final class ValidationErrorField {

  private final String field;

  private final String reason;

  public static ValidationErrorField of(String field, String reason) {
    return new ValidationErrorField(field, reason);
  }
}
