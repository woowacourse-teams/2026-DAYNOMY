package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCalculationResponse;
import org.grit.daynomy.portfolio.service.PortfolioCalculationService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "포트폴리오", description = "로컬 보유자산의 평가금액과 수익률을 계산합니다.")
@RequiredArgsConstructor
@RequestMapping("/api/portfolio")
@RestController
public class PortfolioController {

  private final PortfolioCalculationService portfolioCalculationService;

  @Operation(summary = "포트폴리오 계산", description = "최근 종가를 기준으로 포트폴리오 평가 결과와 차트용 비중을 계산합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "계산 성공",
        content = @Content(schema = @Schema(implementation = PortfolioCalculationResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "보유자산 입력 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "종가 데이터 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/calculate")
  public PortfolioCalculationResponse calculate(
      @Valid @RequestBody PortfolioCalculateRequest request) {
    return portfolioCalculationService.calculate(request);
  }
}
