package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record FeaturedPeopleResponse(
    @Schema(description = "주요 인물 목록") List<FeaturedPersonResponse> people) {}
