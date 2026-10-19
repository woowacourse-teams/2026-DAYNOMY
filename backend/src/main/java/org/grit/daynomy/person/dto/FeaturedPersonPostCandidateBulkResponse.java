package org.grit.daynomy.person.dto;

import java.util.List;

public record FeaturedPersonPostCandidateBulkResponse(
    List<Success> succeeded, List<Failure> failed) {

  public record Success(Long candidateId, FeaturedPersonPostResponse post) {}

  public record Failure(Long candidateId, String errorCode, String message) {}
}
