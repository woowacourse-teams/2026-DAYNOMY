package org.grit.daynomy.person.dto;

import java.time.Instant;
import java.util.List;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.domain.FeaturedPersonPost;

public record FeaturedPersonResponse(
    Long id,
    String name,
    String xProfileUrl,
    String threadsProfileUrl,
    Instant createdAt,
    List<FeaturedPersonPostResponse> posts) {

  public static FeaturedPersonResponse from(FeaturedPerson person, List<FeaturedPersonPost> posts) {
    return new FeaturedPersonResponse(
        person.getId(),
        person.getName(),
        person.getXProfileUrl(),
        person.getThreadsProfileUrl(),
        person.getCreatedAt(),
        posts.stream().map(FeaturedPersonPostResponse::from).toList());
  }
}
