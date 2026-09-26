package org.grit.daynomy.common.logging;

public enum AutomationType {
  UNKNOWN("unknown"),
  SUSPECTED_BOT("suspected_bot");

  private final String code;

  AutomationType(String code) {
    this.code = code;
  }

  public String code() {
    return code;
  }
}
