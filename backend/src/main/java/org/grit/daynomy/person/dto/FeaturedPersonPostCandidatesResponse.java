package org.grit.daynomy.person.dto;

import java.util.List;

public record FeaturedPersonPostCandidatesResponse(
    List<FeaturedPersonPostCandidateResponse> candidates) {}
