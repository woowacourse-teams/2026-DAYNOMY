package org.grit.daynomy.news.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AdminNewsGenerationResponse(
    @Schema(description = "생성되어 저장된 뉴스 수", example = "2") int savedCount) {}
