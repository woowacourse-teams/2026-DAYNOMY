package org.grit.daynomy.external.openai;

public class OpenAiContentTranslationException extends RuntimeException {

  private final String reasonCode;
  private final OpenAiHttpFailure httpFailure;

  public OpenAiContentTranslationException(String message) {
    this("UNKNOWN", message);
  }

  public OpenAiContentTranslationException(String message, Throwable cause) {
    this("UNKNOWN", message, cause);
  }

  public OpenAiContentTranslationException(String reasonCode, String message) {
    super(message);
    this.reasonCode = reasonCode;
    this.httpFailure = null;
  }

  public OpenAiContentTranslationException(String reasonCode, String message, Throwable cause) {
    this(reasonCode, message, cause, null);
  }

  public OpenAiContentTranslationException(
      String reasonCode, String message, Throwable cause, OpenAiHttpFailure httpFailure) {
    super(message, cause);
    this.reasonCode = reasonCode;
    this.httpFailure = httpFailure;
  }

  public String reasonCode() {
    return reasonCode;
  }

  public OpenAiHttpFailure httpFailure() {
    return httpFailure;
  }
}
