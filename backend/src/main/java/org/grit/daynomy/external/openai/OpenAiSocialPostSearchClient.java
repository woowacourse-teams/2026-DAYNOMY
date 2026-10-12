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
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
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
        RestClient.builder()
            .baseUrl(properties.baseUrl())
            .requestFactory(requestFactory)
            .build();
  }

  public List<SocialPostCandidate> search(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }
    if (properties.apiKey() == null || properties.apiKey().isBlank()) {
      throw searchFailed();
    }

    try {
      String response =
          restClient
              .post()
              .uri("/responses")
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers -> headers.setBearerAuth(properties.apiKey()))
              .body(requestBody(keyword.strip()))
              .retrieve()
              .body(String.class);
      return parseCandidates(response);
    } catch (BusinessException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      log.warn("OpenAI social post search failed: {}", exception.getClass().getSimpleName());
      throw searchFailed();
    }
  }

  private Map<String, Object> requestBody(String keyword) {
    return Map.of(
        "model",
        properties.socialSearchModel(),
        "tools",
        List.of(
            Map.of(
                "type",
                "web_search",
                "filters",
                Map.of("allowed_domains", ALLOWED_DOMAINS))),
        "tool_choice",
        "required",
        "include",
        List.of("web_search_call.action.sources"),
        "input",
        "Find up to 10 public Threads and X posts directly related to this search target: "
            + keyword
            + ". The target may be a person, company, topic, or include official profile URLs. Search both platforms and prioritize posts from the supplied profile URLs when present. Do not return profiles, search pages, or home pages. For each post, return publishedDate as YYYY-MM-DD only when the date is clearly confirmed; otherwise return an empty string. Never guess a date.",
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

  private BusinessException searchFailed() {
    return new BusinessException(ExternalErrorCode.AI_SOCIAL_SEARCH_FAILED);
  }

  private static int toMillis(Duration duration) {
    return Math.toIntExact(duration.toMillis());
  }
}
