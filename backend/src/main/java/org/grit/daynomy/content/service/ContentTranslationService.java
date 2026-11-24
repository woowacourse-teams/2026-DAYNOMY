package org.grit.daynomy.content.service;

import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.logging.ExternalTransportFailure;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.openai.ContentTranslation;
import org.grit.daynomy.external.openai.OpenAiContentTranslationException;
import org.grit.daynomy.external.openai.OpenAiContentTranslator;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

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

    ContentTranslation translation = translate(item.title(), item.description());
    if (translation == null) {
      return item;
    }
    return new CollectedContentItem(
        translateTitle ? translation.title() : item.title(),
        translateDescription ? translation.description() : item.description(),
        item.url(),
        item.imageUrl(),
        item.publishedDate());
  }

  public String translateTitleIfEnglish(String title) {
    if (!isEnglish(text(title))) {
      return title;
    }
    ContentTranslation translation = translate(title, "");
    return translation == null ? title : translation.title();
  }

  private ContentTranslation translate(String title, String description) {
    long startedAt = System.nanoTime();
    try {
      log.atInfo()
          .addKeyValue("event", LogEvent.EXTERNAL_API_REQUESTED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "external-content-translation")
          .log(LogEvent.EXTERNAL_API_REQUESTED.message());
      ContentTranslation translation = translator.translate(title, description);
      log.atInfo()
          .addKeyValue("event", LogEvent.EXTERNAL_API_COMPLETED.code())
          .addKeyValue("api", "OpenAI")
          .addKeyValue("operation", "external-content-translation")
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.EXTERNAL_API_COMPLETED.message());
      return translation;
    } catch (OpenAiContentTranslationException exception) {
      Throwable cause = exception.getCause();
      var event =
          log.atWarn()
              .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
              .addKeyValue("api", "OpenAI")
              .addKeyValue("operation", "external-content-translation")
              .addKeyValue("reasonCode", exception.reasonCode())
              .addKeyValue(
                  "errorType",
                  cause == null
                      ? exception.getClass().getSimpleName()
                      : cause.getClass().getSimpleName())
              .addKeyValue("durationMs", elapsedMillis(startedAt));
      if (exception.httpFailure() != null) {
        exception.httpFailure().addTo(event);
      } else if (cause instanceof RestClientException) {
        ExternalTransportFailure.from(cause).addTo(event);
      }
      event.log(LogEvent.EXTERNAL_API_FAILED.message());
      return null;
    }
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
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
