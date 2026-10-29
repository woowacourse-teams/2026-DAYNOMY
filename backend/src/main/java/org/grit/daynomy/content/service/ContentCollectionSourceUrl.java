package org.grit.daynomy.content.service;

import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.content.exception.ContentErrorCode;

final class ContentCollectionSourceUrl {

  private static final Set<String> YOUTUBE_HOSTS = Set.of("youtube.com", "www.youtube.com");
  private static final Set<String> X_HOSTS =
      Set.of("x.com", "www.x.com", "twitter.com", "www.twitter.com");
  private static final Set<String> X_RESERVED_PATHS =
      Set.of("/home", "/search", "/explore", "/notifications", "/messages", "/settings", "/i");
  private static final Set<String> THREADS_HOSTS =
      Set.of("threads.net", "www.threads.net", "threads.com", "www.threads.com");

  private ContentCollectionSourceUrl() {}

  static String normalize(ContentSourceType sourceType, String value) {
    try {
      URI uri = URI.create(value.strip());
      String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
      String path = trimTrailingSlash(uri.getPath());
      if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
        throw invalid();
      }
      if (!isValid(sourceType, host, path)) {
        throw invalid();
      }
      return new URI(
              "https",
              null,
              canonicalHost(sourceType),
              -1,
              canonicalPath(sourceType, path),
              null,
              null)
          .toString();
    } catch (BusinessException exception) {
      throw exception;
    } catch (RuntimeException | java.net.URISyntaxException exception) {
      throw invalid();
    }
  }

  static boolean belongsToSource(ContentSourceType sourceType, String sourceUrl, String postUrl) {
    if (sourceType != ContentSourceType.X && sourceType != ContentSourceType.THREADS) {
      return false;
    }
    try {
      URI source = URI.create(normalize(sourceType, sourceUrl));
      URI post = URI.create(postUrl);
      String sourceHandle = handle(sourceType, source.getPath());
      String postHandle = handle(sourceType, post.getPath());
      return sourceHandle != null
          && postHandle != null
          && sourceHandle.equalsIgnoreCase(postHandle);
    } catch (BusinessException | IllegalArgumentException exception) {
      return false;
    }
  }

  private static String handle(ContentSourceType sourceType, String path) {
    String[] segments = path == null ? new String[0] : path.split("/");
    if (segments.length < 2 || segments[1].isBlank()) {
      return null;
    }
    if (sourceType == ContentSourceType.X) {
      return segments.length == 2
              || (segments.length >= 4 && "status".equalsIgnoreCase(segments[2]))
          ? segments[1]
          : null;
    }
    if (!segments[1].startsWith("@")) {
      return null;
    }
    return segments.length == 2 || (segments.length >= 4 && "post".equalsIgnoreCase(segments[2]))
        ? segments[1].substring(1)
        : null;
  }

  private static boolean isValid(ContentSourceType sourceType, String host, String path) {
    return switch (sourceType) {
      case YOUTUBE ->
          YOUTUBE_HOSTS.contains(host)
              && (path.matches("/@[^/]+")
                  || path.matches("/channel/[^/]+")
                  || path.matches("/user/[^/]+"));
      case X ->
          X_HOSTS.contains(host)
              && path.matches("/[^/]+")
              && X_RESERVED_PATHS.stream().noneMatch(path::equalsIgnoreCase);
      case THREADS -> THREADS_HOSTS.contains(host) && path.matches("/@[^/]+");
      default -> false;
    };
  }

  private static String canonicalHost(ContentSourceType sourceType) {
    return switch (sourceType) {
      case YOUTUBE -> "www.youtube.com";
      case X -> "x.com";
      case THREADS -> "www.threads.com";
      default -> throw invalid();
    };
  }

  private static String canonicalPath(ContentSourceType sourceType, String path) {
    return sourceType == ContentSourceType.X || sourceType == ContentSourceType.THREADS
        ? path.toLowerCase(Locale.ROOT)
        : path;
  }

  private static String trimTrailingSlash(String path) {
    if (path == null || path.isBlank() || "/".equals(path)) {
      return "/";
    }
    return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
  }

  private static BusinessException invalid() {
    return new BusinessException(ContentErrorCode.CONTENT_COLLECTION_SOURCE_INVALID);
  }
}
