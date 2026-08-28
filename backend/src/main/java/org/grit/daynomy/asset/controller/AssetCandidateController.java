package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.AssetCandidatesResponse;
import org.grit.daynomy.asset.service.AssetCandidateService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "AssetCandidate", description = "북마크 후보 자산 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/assets")
@RestController
public class AssetCandidateController {

  private final AssetCandidateService assetCandidateService;

  @Operation(
      summary = "코스닥 대표 종목 순위 조회",
      description = "KOSDAQ 시장 종목 중 시가총액 상위 150개를 검색어와 페이지 조건으로 조회합니다.")
  @GetMapping("/kosdaq/top")
  public ResponseEntity<AssetCandidatesResponse> getKosdaqTopRankings(
      @Parameter(
              description = "종목명 또는 종목코드 검색어. 생략하면 전체 조회하며, 입력 시 문자·숫자를 포함해야 합니다(최대 100자).",
              example = "에코프로")
          @RequestParam(name = "q", required = false)
          @Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
          @Pattern(regexp = ".*[\\p{L}\\p{N}].*", message = "올바른 검색어를 입력해주세요.")
          String keyword,
      @Parameter(description = "1부터 시작하는 페이지 번호", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
          int page,
      @Parameter(description = "페이지 크기", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size) {
    return ResponseEntity.ok(assetCandidateService.getKosdaqTopRankings(keyword, page, size));
  }
}
