package org.grit.daynomy.external.openai;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

final class OpenAiPromptLoader {

  private OpenAiPromptLoader() {}

  static String load(String path) {
    try (InputStream input = new ClassPathResource(path).getInputStream()) {
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException exception) {
      throw new IllegalStateException(
          "OpenAI prompt resource could not be loaded: " + path, exception);
    }
  }
}
