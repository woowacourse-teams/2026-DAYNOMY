package org.grit.daynomy.content.repository;

import java.time.Instant;

public interface ContentPopularityProjection {
  String getContentType();

  Long getContentId();

  Long getScore();

  Instant getLastInteractedAt();
}
