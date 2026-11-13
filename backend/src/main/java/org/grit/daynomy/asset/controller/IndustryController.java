package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.IndustriesResponse;
import org.grit.daynomy.asset.service.IndustryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "업종", description = "소식과 종목에 사용할 업종을 조회합니다.")
@RequiredArgsConstructor
@RequestMapping("/api/industries")
@RestController
public class IndustryController {

  private final IndustryService industryService;

  @Operation(summary = "활성 업종 목록 조회")
  @GetMapping
  public IndustriesResponse getIndustries() {
    return industryService.getActiveIndustries();
  }
}
