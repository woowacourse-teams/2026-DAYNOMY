package org.grit.daynomy.external.wikimedia;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
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
  private static final int MAX_IMAGE_SIZE_BYTES = 5 * 1024 * 1024;
  private static final int SEARCH_LIMIT = 50;
  private static final int THUMBNAIL_WIDTH = 1_200;
  private static final Pattern CC0_LICENSE = Pattern.compile("^cc0(?: \\d+(?:\\.\\d+)?)?$");
  private static final Pattern CC_BY_LICENSE =
      Pattern.compile("^cc by(?: \\d+(?:\\.\\d+)?(?: [a-z]{2})?)?$");
  private static final String PUBLIC_DOMAIN_LICENSE = "public domain";
  private static final byte[] JPEG_SIGNATURE = {(byte) 0xff, (byte) 0xd8, (byte) 0xff};
  private static final byte[] PNG_SIGNATURE = {
    (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
  };
  private static final byte[] RIFF_SIGNATURE = {'R', 'I', 'F', 'F'};
  private static final byte[] WEBP_SIGNATURE = {'W', 'E', 'B', 'P'};
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
    this.restClient =
        RestClient.builder().baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
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
      DownloadedImage downloadedImage =
          RestClient.builder()
              .requestFactory(requestFactory())
              .build()
              .get()
              .uri(URI.create(candidate.thumbnailUrl()))
              .header(HttpHeaders.USER_AGENT, userAgent())
              .exchange(
                  (request, response) -> {
                    if (!response.getStatusCode().is2xxSuccessful()) {
                      throw new RestClientException("Wikimedia Commons image download failed");
                    }
                    byte[] content = readContent(response.getBody());
                    return new DownloadedImage(
                        content,
                        resolveContentType(response.getHeaders().getContentType(), content));
                  });
      return new ImportedImage(
          candidate,
          downloadedImage.content(),
          downloadedImage.contentType(),
          extension(downloadedImage.contentType()));
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

  private byte[] readContent(InputStream input) throws IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    byte[] buffer = new byte[8_192];
    int totalBytes = 0;
    while (totalBytes <= MAX_IMAGE_SIZE_BYTES) {
      int bytesToRead = Math.min(buffer.length, MAX_IMAGE_SIZE_BYTES + 1 - totalBytes);
      int read = input.read(buffer, 0, bytesToRead);
      if (read == -1) {
        break;
      }
      totalBytes += read;
      if (totalBytes > MAX_IMAGE_SIZE_BYTES) {
        throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
      }
      output.write(buffer, 0, read);
    }
    if (totalBytes == 0) {
      throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
    }
    return output.toByteArray();
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
          .orElseThrow(
              () -> new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED));
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
        String sourceUrl =
            "https://commons.wikimedia.org/wiki/" + page.path("title").asText().replace(' ', '_');
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
    return PUBLIC_DOMAIN_LICENSE.equals(normalized)
        || CC0_LICENSE.matcher(normalized).matches()
        || CC_BY_LICENSE.matcher(normalized).matches();
  }

  private static boolean isSupportedMime(String mime) {
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

  static String resolveContentType(MediaType responseContentType, byte[] content) {
    String detectedContentType = detectContentType(content);
    if (detectedContentType != null) {
      return detectedContentType;
    }
    if (responseContentType != null) {
      String headerContentType =
          "%s/%s".formatted(responseContentType.getType(), responseContentType.getSubtype());
      if (isSupportedMime(headerContentType)) {
        return headerContentType;
      }
    }
    throw new BusinessException(ExternalErrorCode.WIKIMEDIA_IMAGE_REQUEST_FAILED);
  }

  private static String detectContentType(byte[] content) {
    if (hasSignature(content, 0, JPEG_SIGNATURE)) {
      return MediaType.IMAGE_JPEG_VALUE;
    }
    if (hasSignature(content, 0, PNG_SIGNATURE)) {
      return MediaType.IMAGE_PNG_VALUE;
    }
    if (hasSignature(content, 0, RIFF_SIGNATURE) && hasSignature(content, 8, WEBP_SIGNATURE)) {
      return IMAGE_WEBP;
    }
    return null;
  }

  private static boolean hasSignature(byte[] content, int offset, byte[] signature) {
    if (content == null || content.length < offset + signature.length) {
      return false;
    }
    for (int index = 0; index < signature.length; index++) {
      if (content[offset + index] != signature[index]) {
        return false;
      }
    }
    return true;
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

  private record DownloadedImage(byte[] content, String contentType) {}

  public record ImportedImage(
      WikimediaImageCandidate candidate, byte[] content, String contentType, String extension) {}
}
