package org.grit.daynomy.person.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.person.dto.FeaturedPeopleResponse;
import org.grit.daynomy.person.service.FeaturedPersonService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "주요 인물", description = "주요 인물과 공개 소셜 게시물 조회 API")
@RequiredArgsConstructor
@RequestMapping("/api/people")
@RestController
public class FeaturedPersonController {

  private final FeaturedPersonService featuredPersonService;

  @Operation(summary = "주요 인물 목록 조회")
  @GetMapping
  public ResponseEntity<FeaturedPeopleResponse> getPeople() {
    return ResponseEntity.ok(featuredPersonService.getPeople());
  }
}
