package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FeaturedPersonPostCandidatesResponse(
    @Schema(description = "게시물 후보 목록") List<FeaturedPersonPostCandidateResponse> candidates) {}
