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
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.dto.StockPriceResponse;
import org.grit.daynomy.asset.dto.StockPricesResponse;
import org.grit.daynomy.asset.dto.StockSearchResponse;
import org.grit.daynomy.asset.service.StockPriceService;
import org.grit.daynomy.asset.service.StockSearchService;
import org.grit.daynomy.common.response.ErrorResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "국내 주식·ETF", description = "포트폴리오에 추가할 국내 주식과 ETF를 조회합니다.")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/stocks")
@RestController
public class StockController {

  private final StockSearchService stockSearchService;
  private final StockPriceService stockPriceService;

  @Operation(summary = "국내 주식·ETF 통합 검색", description = "종목명 또는 종목코드로 상장 주식과 ETF를 최대 20개 검색합니다.")
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

  @Operation(summary = "국내 주식·ETF 최근 종가 조회", description = "저장된 가장 최근 거래일의 종가를 조회합니다.")
  @GetMapping("/{assetId}/price")
  public StockPriceResponse getLatestPrice(@PathVariable Long assetId) {
    return stockPriceService.getLatestPrice(assetId);
  }

  @Operation(summary = "국내 주식·ETF 기간별 종가 조회", description = "여러 종목의 31일 이내 거래일별 종가를 조회합니다.")
  @GetMapping("/prices")
  public StockPricesResponse getPrices(
      @RequestParam @Size(min = 1, max = 50) List<@Positive Long> assetIds,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
    return stockPriceService.getPrices(assetIds, from, to);
  }
}
