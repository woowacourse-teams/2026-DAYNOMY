package org.grit.daynomy.asset.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.StockSearchResponse;
import org.grit.daynomy.asset.service.StockSearchService;
import org.grit.daynomy.common.response.ErrorResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "국내 주식", description = "포트폴리오에 추가할 국내 주식을 조회합니다.")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
@RestController
public class StockController {

  private final StockSearchService stockSearchService;

  @Operation(summary = "국내 주식 검색", description = "종목명 또는 종목코드로 상장 종목을 최대 20개 검색합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "검색 성공 또는 검색 결과 없음",
        content = @Content(schema = @Schema(implementation = StockSearchResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "검색어 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public StockSearchResponse search(
      @Parameter(description = "종목명 또는 종목코드(1~50자)", example = "삼성", required = true)
          @RequestParam(name = "q", required = false)
          @NotBlank(message = "검색어를 입력해주세요.")
          @Size(max = 50, message = "검색어는 50자 이하여야 합니다.")
          @Pattern(regexp = ".*[\\p{L}\\p{N}].*", message = "올바른 검색어를 입력해주세요.")
          String keyword) {
    return stockSearchService.search(keyword);
  }
}
