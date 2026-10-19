package org.grit.daynomy.external.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.common.logging.LogEvent;
import org.grit.daynomy.external.ExternalErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;

@Slf4j
@Component
public class OpenAiSocialPostSearchClient {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int SEARCH_LIMIT = 10;
  private static final Pattern THREADS_POST_PATH =
      Pattern.compile("^(?:/@[^/]+/post|/t)/[^/]+/?$", Pattern.CASE_INSENSITIVE);
  private static final Pattern X_POST_PATH =
      Pattern.compile("^/[^/]+/status/\\d+/?$", Pattern.CASE_INSENSITIVE);
  private static final List<String> ALLOWED_DOMAINS =
      List.of("threads.net", "threads.com", "x.com", "twitter.com");

  private final OpenAiProperties properties;
  private final RestClient restClient;

  public OpenAiSocialPostSearchClient(OpenAiProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(properties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(properties.readTimeout()));
    this.restClient =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
  }

  public List<SocialPostCandidate> search(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }
    return execute(
        "searchPosts",
        "Find up to 10 public Threads and X posts directly related to this search target: "
            + keyword.strip()
            + ". The target may be a person, company, topic, or include official profile URLs."
            + " Search both platforms and prioritize posts from the supplied profile URLs when"
            + " present. Do not return profiles, search pages, or home pages. For each post, return"
            + " publishedDate as YYYY-MM-DD only when the date is clearly confirmed; otherwise"
            + " return an empty string. Never guess a date.");
  }

  public List<SocialPostCandidate> searchProfiles(
      String xProfileUrl, String threadsProfileUrl, LocalDate from, LocalDate to) {
    String xHandle = profileHandle(xProfileUrl, SocialPostCandidate.Platform.X);
    String threadsHandle = profileHandle(threadsProfileUrl, SocialPostCandidate.Platform.THREADS);
    if (xHandle == null && threadsHandle == null) {
      return List.of();
    }
    String target =
        List.of(
                xProfileUrl == null ? "" : xProfileUrl,
                threadsProfileUrl == null ? "" : threadsProfileUrl)
            .stream()
            .filter(value -> !value.isBlank())
            .reduce((left, right) -> left + " " + right)
            .orElse("");
    return execute(
            "searchProfilePosts",
            "Find up to 10 public posts authored only by these exact official profiles: "
                + target
                + ". Return a post only when the author handle in its URL exactly matches one of"
                + " the supplied profiles. Do not return mentions, reposts from other accounts,"
                + " profiles, search pages, or home pages. For each post, return publishedDate as"
                + " YYYY-MM-DD only when clearly confirmed. Search only posts published from "
                + from
                + " through "
                + to
                + " inclusive. Never guess a date, and exclude posts whose date cannot be"
                + " confirmed.")
        .stream()
        .filter(candidate -> belongsToProfile(candidate, xHandle, threadsHandle))
        .filter(candidate -> isWithinPeriod(candidate.publishedDate(), from, to))
        .toList();
  }

  private boolean isWithinPeriod(String value, LocalDate from, LocalDate to) {
    Optional<LocalDate> publishedDate = parsePublishedDate(value);
    return publishedDate.isPresent()
        && !publishedDate.get().isBefore(from)
        && !publishedDate.get().isAfter(to);
  }

  private List<SocialPostCandidate> execute(String operation, String input) {
    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
      throw searchFailed();
    }

    long startedAt = System.nanoTime();
    log.atDebug()
        .addKeyValue("event", LogEvent.EXTERNAL_API_REQUESTED.code())
        .addKeyValue("api", "OPENAI_SOCIAL_SEARCH")
        .addKeyValue("operation", operation)
        .log(LogEvent.EXTERNAL_API_REQUESTED.message());
    try {
      String response =
          restClient
              .post()
              .uri("/responses")
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers -> headers.setBearerAuth(properties.apiKey()))
              .body(requestBody(input))
              .retrieve()
              .body(String.class);
      List<SocialPostCandidate> candidates = parseCandidates(response);
      log.atInfo()
          .addKeyValue("event", LogEvent.EXTERNAL_API_COMPLETED.code())
          .addKeyValue("api", "OPENAI_SOCIAL_SEARCH")
          .addKeyValue("operation", operation)
          .addKeyValue("responseCount", candidates.size())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .log(LogEvent.EXTERNAL_API_COMPLETED.message());
      return candidates;
    } catch (HttpStatusCodeException exception) {
      log.atError()
          .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
          .addKeyValue("api", "OPENAI_SOCIAL_SEARCH")
          .addKeyValue("operation", operation)
          .addKeyValue("responseStatus", exception.getStatusCode().value())
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorCode", ExternalErrorCode.AI_SOCIAL_SEARCH_FAILED.code())
          .addKeyValue("exceptionType", exception.getClass().getSimpleName())
          .log(LogEvent.EXTERNAL_API_FAILED.message());
      throw searchFailed();
    } catch (BusinessException exception) {
      log.atError()
          .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
          .addKeyValue("api", "OPENAI_SOCIAL_SEARCH")
          .addKeyValue("operation", operation)
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorCode", exception.errorCode().code())
          .addKeyValue("reasonCode", "INVALID_RESPONSE")
          .log(LogEvent.EXTERNAL_API_FAILED.message());
      throw exception;
    } catch (RuntimeException exception) {
      log.atError()
          .addKeyValue("event", LogEvent.EXTERNAL_API_FAILED.code())
          .addKeyValue("api", "OPENAI_SOCIAL_SEARCH")
          .addKeyValue("operation", operation)
          .addKeyValue("durationMs", elapsedMillis(startedAt))
          .addKeyValue("errorCode", ExternalErrorCode.AI_SOCIAL_SEARCH_FAILED.code())
          .addKeyValue("exceptionType", exception.getClass().getSimpleName())
          .log(LogEvent.EXTERNAL_API_FAILED.message());
      throw searchFailed();
    }
  }

  private Map<String, Object> requestBody(String input) {
    return Map.of(
        "model",
        properties.socialSearchModel(),
        "tools",
        List.of(
            Map.of("type", "web_search", "filters", Map.of("allowed_domains", ALLOWED_DOMAINS))),
        "tool_choice",
        "required",
        "include",
        List.of("web_search_call.action.sources"),
        "input",
        input,
        "text",
        Map.of("format", responseFormat()));
  }

  private Map<String, Object> responseFormat() {
    return Map.of(
        "type",
        "json_schema",
        "name",
        "social_post_search",
        "strict",
        true,
        "schema",
        Map.of(
            "type",
            "object",
            "additionalProperties",
            false,
            "properties",
            Map.of(
                "items",
                Map.of(
                    "type",
                    "array",
                    "maxItems",
                    SEARCH_LIMIT,
                    "items",
                    Map.of(
                        "type",
                        "object",
                        "additionalProperties",
                        false,
                        "properties",
                        Map.of(
                            "title",
                            Map.of("type", "string"),
                            "url",
                            Map.of("type", "string"),
                            "publishedDate",
                            Map.of("type", "string")),
                        "required",
                        List.of("title", "url", "publishedDate")))),
            "required",
            List.of("items")));
  }

  private List<SocialPostCandidate> parseCandidates(String response) {
    try {
      JsonNode output = OBJECT_MAPPER.readTree(response).path("output");
      if (!output.isArray()) {
        throw new IllegalArgumentException("OpenAI response does not contain output");
      }

      Map<String, SocialPostCandidate> sources = new LinkedHashMap<>();
      for (JsonNode outputItem : output) {
        if (!"web_search_call".equals(outputItem.path("type").asText())) {
          continue;
        }
        JsonNode sourceNodes = outputItem.path("action").path("sources");
        if (!sourceNodes.isArray()) {
          continue;
        }
        for (JsonNode source : sourceNodes) {
          candidate(source.path("title").asText(), source.path("url").asText())
              .ifPresent(candidate -> sources.putIfAbsent(candidate.url(), candidate));
        }
      }

      Map<String, SocialPostCandidate> candidates = new LinkedHashMap<>();
      for (JsonNode item : outputItems(output)) {
        Optional<SocialPostCandidate> parsedCandidate =
            candidate(item.path("title").asText(), item.path("url").asText());
        if (parsedCandidate.isEmpty()) {
          continue;
        }
        SocialPostCandidate source = sources.get(parsedCandidate.get().url());
        if (source == null) {
          continue;
        }
        Optional<LocalDate> publishedDate = parsePublishedDate(item.path("publishedDate").asText());
        candidates.putIfAbsent(
            source.url(),
            new SocialPostCandidate(
                source.platform(),
                parsedCandidate.get().title(),
                source.url(),
                publishedDate.map(LocalDate::toString).orElse("")));
      }
      return List.copyOf(candidates.values());
    } catch (Exception exception) {
      throw searchFailed();
    }
  }

  private JsonNode outputItems(JsonNode output) throws JsonProcessingException {
    for (JsonNode outputItem : output) {
      JsonNode content = outputItem.path("content");
      if (!content.isArray()) {
        continue;
      }
      for (JsonNode contentItem : content) {
        if ("output_text".equals(contentItem.path("type").asText())) {
          JsonNode items = OBJECT_MAPPER.readTree(contentItem.path("text").asText()).path("items");
          if (items.isArray()) {
            return items;
          }
        }
      }
    }
    throw new IllegalArgumentException("OpenAI response does not contain social post items");
  }

  private Optional<SocialPostCandidate> candidate(String rawTitle, String rawUrl) {
    if (rawUrl.isBlank()) {
      return Optional.empty();
    }

    try {
      URI uri = URI.create(rawUrl);
      String scheme = uri.getScheme();
      String host = uri.getHost();
      String path = uri.getRawPath();
      if ((!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme))
          || host == null
          || path == null) {
        return Optional.empty();
      }

      String normalizedHost = host.toLowerCase(Locale.ROOT);
      SocialPostCandidate.Platform platform = platform(normalizedHost, path);
      if (platform == null) {
        return Optional.empty();
      }

      String normalizedPath = path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
      String normalizedUrl =
          new URI("https", null, normalizedHost, -1, normalizedPath, null, null).toString();
      String title = rawTitle.strip();
      if (title.isBlank()) {
        title = platform == SocialPostCandidate.Platform.THREADS ? "Threads 게시물" : "X 게시물";
      }
      title = title.replaceAll("\\s+", " ");
      if (title.length() > 255) {
        title = title.substring(0, 255);
      }
      return Optional.of(new SocialPostCandidate(platform, title, normalizedUrl, ""));
    } catch (IllegalArgumentException | URISyntaxException exception) {
      return Optional.empty();
    }
  }

  private Optional<LocalDate> parsePublishedDate(String value) {
    try {
      return value == null || value.isBlank()
          ? Optional.empty()
          : Optional.of(LocalDate.parse(value.strip()));
    } catch (DateTimeParseException exception) {
      return Optional.empty();
    }
  }

  private SocialPostCandidate.Platform platform(String host, String path) {
    if (("threads.net".equals(host)
            || "www.threads.net".equals(host)
            || "threads.com".equals(host)
            || "www.threads.com".equals(host))
        && THREADS_POST_PATH.matcher(path).matches()) {
      return SocialPostCandidate.Platform.THREADS;
    }
    if (("x.com".equals(host)
            || "www.x.com".equals(host)
            || "twitter.com".equals(host)
            || "www.twitter.com".equals(host))
        && X_POST_PATH.matcher(path).matches()) {
      return SocialPostCandidate.Platform.X;
    }
    return null;
  }

  private boolean belongsToProfile(
      SocialPostCandidate candidate, String xHandle, String threadsHandle) {
    String expectedHandle =
        candidate.platform() == SocialPostCandidate.Platform.X ? xHandle : threadsHandle;
    if (expectedHandle == null) {
      return false;
    }
    try {
      String path = URI.create(candidate.url()).getPath();
      String[] segments = path.split("/");
      if (candidate.platform() == SocialPostCandidate.Platform.X) {
        return segments.length >= 4 && expectedHandle.equalsIgnoreCase(segments[1]);
      }
      return segments.length >= 4
          && segments[1].startsWith("@")
          && expectedHandle.equalsIgnoreCase(segments[1].substring(1));
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private String profileHandle(String profileUrl, SocialPostCandidate.Platform platform) {
    if (profileUrl == null || profileUrl.isBlank()) {
      return null;
    }
    try {
      String path = URI.create(profileUrl).getPath();
      if (path == null) {
        return null;
      }
      String[] segments = path.split("/");
      if (segments.length < 2 || segments[1].isBlank()) {
        return null;
      }
      if (platform == SocialPostCandidate.Platform.THREADS && !segments[1].startsWith("@")) {
        return null;
      }
      return platform == SocialPostCandidate.Platform.THREADS
          ? segments[1].substring(1)
          : segments[1];
    } catch (IllegalArgumentException exception) {
      return null;
    }
  }

  private BusinessException searchFailed() {
    return new BusinessException(ExternalErrorCode.AI_SOCIAL_SEARCH_FAILED);
  }

  private static int toMillis(Duration duration) {
    return Math.toIntExact(duration.toMillis());
  }

  private long elapsedMillis(long startedAt) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
  }
}
