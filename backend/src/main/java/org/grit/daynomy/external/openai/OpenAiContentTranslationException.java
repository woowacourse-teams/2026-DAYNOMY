package org.grit.daynomy.external.openai;

public class OpenAiContentTranslationException extends RuntimeException {

  public OpenAiContentTranslationException(String message) {
    super(message);
  }

  public OpenAiContentTranslationException(String message, Throwable cause) {
    super(message, cause);
  }
}
