package org.grit.daynomy.content.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.openai.ContentTranslation;
import org.grit.daynomy.external.openai.OpenAiContentTranslationException;
import org.grit.daynomy.external.openai.OpenAiContentTranslator;
import org.springframework.stereotype.Service;

@Slf4j
@RequiredArgsConstructor
@Service
public class ContentTranslationService {

  private final OpenAiContentTranslator translator;

  CollectedContentItem translateIfEnglish(CollectedContentItem item) {
    boolean translateTitle = isEnglish(text(item.title()));
    boolean translateDescription = isEnglish(text(item.description()));
    if (!translateTitle && !translateDescription) {
      return item;
    }

    try {
      log.atInfo()
          .addKeyValue("event", LogEvent.EXTERNAL_API_REQUESTED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "external-content-translation")
          .log(LogEvent.EXTERNAL_API_REQUESTED.message());
      ContentTranslation translation = translator.translate(item.title(), item.description());
      log.atInfo()
          .addKeyValue("event", LogEvent.EXTERNAL_API_COMPLETED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "external-content-translation")
          .log(LogEvent.EXTERNAL_API_COMPLETED.message());
      return new CollectedContentItem(
          translateTitle ? translation.title() : item.title(),
          translateDescription ? translation.description() : item.description(),
          item.url(),
          item.imageUrl(),
          item.publishedDate());
    } catch (OpenAiContentTranslationException exception) {
      log.atWarn()
          .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "external-content-translation")
          .addKeyValue("errorType", exception.getClass().getSimpleName())
          .log(LogEvent.EXTERNAL_API_FAILED.message());
      return item;
    }
  }

  static boolean isEnglish(String value) {
    boolean hasLatinLetter = false;
    for (int index = 0; index < value.length(); ) {
      int codePoint = value.codePointAt(index);
      Character.UnicodeScript script = Character.UnicodeScript.of(codePoint);
      if (Character.isLetter(codePoint)) {
        if (script != Character.UnicodeScript.LATIN) {
          return false;
        }
        hasLatinLetter = true;
      }
      index += Character.charCount(codePoint);
    }
    return hasLatinLetter;
  }

  private String text(String value) {
    return value == null ? "" : value;
  }
}
