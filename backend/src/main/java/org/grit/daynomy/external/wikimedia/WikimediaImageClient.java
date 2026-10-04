package org.grit.daynomy.external.wikimedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.external.ExternalErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class WikimediaImageClient {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final int SEARCH_LIMIT = 50;
  private static final int THUMBNAIL_WIDTH = 1_200;
  private static final String COMMONS_HOST = "commons.wikimedia.org";
  private static final String UPLOAD_HOST = "upload.wikimedia.org";
  private static final String THUMB_HOST = "thumb.wikimedia.org";
  private static final String IMAGE_WEBP = "image/webp";

  private final WikimediaProperties properties;
  private final RestClient restClient;

  public WikimediaImageClient(WikimediaProperties properties) {
    this.properties = properties;
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(properties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(properties.readTimeout()));
    this.restClient = RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
  }

  public List<WikimediaImageCandidate> search(String keyword) {
    if (keyword == null || keyword.isBlank()) {
      return List.of();
    }

    try {
      String response =
          restClient
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/w/api.php")
                          .queryParam("action", "query")
                          .queryParam("generator", "search")
                          .queryParam("gsrsearch", keyword.strip())
                          .queryParam("gsrnamespace", 6)
                          .queryParam("gsrlimit", SEARCH_LIMIT)
                          .queryParam("prop", "imageinfo")
                          .queryParam("iiprop", "url|mime|size|extmetadata")
                          .queryParam("iiurlwidth", THUMBNAIL_WIDTH)
                          .queryParam("format", "json")
                          .queryParam("formatversion", 2)
                          .build())
              .header(HttpHeaders.USER_AGENT, userAgent())
              .retrieve()
              .body(String.class);
      return parseCandidates(response);
    } catch (HttpStatusCodeException exception) {
      log.warn("Wikimedia Commons image search failed", exception);
      throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
    } catch (RestClientException exception) {
      log.warn("Wikimedia Commons image search failed", exception);
      throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
    }
  }

  public ImportedImage download(String title) {
    try {
      WikimediaImageCandidate candidate = findCandidate(title);
      byte[] content =
          RestClient.builder()
              .requestFactory(requestFactory())
              .build()
              .get()
              .uri(URI.create(candidate.thumbnailUrl()))
              .header(HttpHeaders.USER_AGENT, userAgent())
              .retrieve()
              .body(byte[].class);
      if (content == null || content.length == 0 || content.length > 5 * 1024 * 1024) {
        throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
      }
      String contentType = mediaType(candidate.thumbnailUrl());
      return new ImportedImage(candidate, content, contentType, extension(contentType));
    } catch (BusinessException exception) {
      throw exception;
    } catch (HttpStatusCodeException exception) {
      log.warn("Wikimedia Commons image download failed", exception);
      throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
    } catch (RestClientException | IllegalArgumentException exception) {
      log.warn("Wikimedia Commons image download failed", exception);
      throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
    }
  }

  private WikimediaImageCandidate findCandidate(String title) {
    try {
      String response =
          restClient
              .get()
              .uri(
                  builder ->
                      builder
                          .path("/w/api.php")
                          .queryParam("action", "query")
                          .queryParam("titles", title)
                          .queryParam("prop", "imageinfo")
                          .queryParam("iiprop", "url|mime|size|extmetadata")
                          .queryParam("iiurlwidth", THUMBNAIL_WIDTH)
                          .queryParam("format", "json")
                          .queryParam("formatversion", 2)
                          .build())
              .header(HttpHeaders.USER_AGENT, userAgent())
              .retrieve()
              .body(String.class);
      return parseCandidates(response).stream()
          .filter(candidate -> candidate.title().equals(title))
          .findFirst()
          .orElseThrow(() -> new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED));
    } catch (BusinessException exception) {
      throw exception;
    } catch (HttpStatusCodeException exception) {
      throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
    } catch (RestClientException exception) {
      throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
    }
  }

  private List<WikimediaImageCandidate> parseCandidates(String response) {
    try {
      JsonNode pages = OBJECT_MAPPER.readTree(response).path("query").path("pages");
      List<WikimediaImageCandidate> candidates = new ArrayList<>();
      for (JsonNode page : pages) {
        JsonNode info = page.path("imageinfo").path(0);
        String mime = info.path("mime").asText();
        String license = metadata(info, "LicenseShortName");
        String licenseUrl = metadata(info, "LicenseUrl");
        String thumbnailUrl = info.path("thumburl").asText();
        String sourceUrl = "https://commons.wikimedia.org/wiki/" + page.path("title").asText().replace(' ', '_');
        if (!isSupportedMime(mime)
            || thumbnailUrl.isBlank()
            || !isAllowedLicense(license)
            || !isAllowedHost(thumbnailUrl)) {
          continue;
        }
        candidates.add(
            new WikimediaImageCandidate(
                page.path("title").asText(),
                thumbnailUrl,
                sourceUrl,
                cleanMetadata(metadata(info, "Artist")),
                license,
                licenseUrl,
                info.path("width").asInt(),
                info.path("height").asInt()));
      }
      return List.copyOf(candidates);
    } catch (Exception exception) {
      throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
    }
  }

  private boolean isAllowedLicense(String license) {
    String normalized = license == null ? "" : license.toLowerCase(Locale.ROOT).strip();
    return normalized.contains("public domain")
        || normalized.contains("cc0")
        || normalized.equals("cc by")
        || normalized.startsWith("cc by ");
  }

  private boolean isSupportedMime(String mime) {
    return MediaType.IMAGE_JPEG_VALUE.equals(mime)
        || MediaType.IMAGE_PNG_VALUE.equals(mime)
        || IMAGE_WEBP.equals(mime);
  }

  private boolean isAllowedHost(String url) {
    try {
      String host = URI.create(url).getHost();
      return COMMONS_HOST.equalsIgnoreCase(host)
          || UPLOAD_HOST.equalsIgnoreCase(host)
          || THUMB_HOST.equalsIgnoreCase(host);
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private String metadata(JsonNode imageInfo, String name) {
    return imageInfo.path("extmetadata").path(name).path("value").asText("");
  }

  private String cleanMetadata(String value) {
    return value.replaceAll("<[^>]*>", "").replaceAll("\\s+", " ").strip();
  }

  private String mediaType(String url) {
    String lower = url.toLowerCase();
    if (lower.contains(".png")) {
      return MediaType.IMAGE_PNG_VALUE;
    }
    if (lower.contains(".webp")) {
      return IMAGE_WEBP;
    }
    return MediaType.IMAGE_JPEG_VALUE;
  }

  private String extension(String contentType) {
    return MediaType.IMAGE_PNG_VALUE.equals(contentType)
        ? "png"
        : IMAGE_WEBP.equals(contentType) ? "webp" : "jpg";
  }

  private SimpleClientHttpRequestFactory requestFactory() {
    SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
    requestFactory.setConnectTimeout(toMillis(properties.connectTimeout()));
    requestFactory.setReadTimeout(toMillis(properties.readTimeout()));
    return requestFactory;
  }

  private String userAgent() {
    return properties.userAgent() == null || properties.userAgent().isBlank()
        ? "daynomy/1.0"
        : properties.userAgent();
  }

  private int toMillis(java.time.Duration timeout) {
    return Math.toIntExact(timeout.toMillis());
  }

  public record ImportedImage(
      WikimediaImageCandidate candidate, byte[] content, String contentType, String extension) {}
}
