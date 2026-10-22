package org.grit.daynomy.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.content.dto.ContentInteractionRequest;
import org.grit.daynomy.content.dto.ContentPopularityResponse;
import org.grit.daynomy.content.service.ContentInteractionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "콘텐츠 인기", description = "콘텐츠 반응 기록 및 인기 점수 조회 API")
@Validated
@RequiredArgsConstructor
@RestController
public class ContentInteractionController {

  private final ContentInteractionService contentInteractionService;
  private final AnonymousContentVisitorCookieManager anonymousVisitorCookieManager;

  @Operation(summary = "콘텐츠 반응 기록")
  @ApiResponses({
    @ApiResponse(
        responseCode = "204",
        description = "반응 기록 성공",
        headers =
            @Header(
                name = "Set-Cookie",
                description = "비회원 식별 쿠키. 기존 유효 쿠키나 로그인 회원 요청에서는 생략될 수 있습니다.",
                schema =
                    @Schema(
                        type = "string",
                        example = "content_visitor=...; Path=/; HttpOnly; SameSite=Lax"))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "공개된 콘텐츠를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "429",
        description = "콘텐츠 반응 요청 제한 초과",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/api/content-interactions")
  public ResponseEntity<Void> record(
      @Valid @RequestBody ContentInteractionRequest request,
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      HttpServletRequest servletRequest,
      HttpServletResponse servletResponse) {
    UUID anonymousVisitorId =
        member == null
            ? anonymousVisitorCookieManager.getOrCreate(servletRequest, servletResponse)
            : null;
    contentInteractionService.record(request, member, anonymousVisitorId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "콘텐츠 인기 점수 조회", description = "지정한 최근 기간의 콘텐츠별 인기 점수를 조회합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = ContentPopularityResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "조회 조건 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/api/content-rankings")
  public ResponseEntity<ContentPopularityResponse> getPopularity(
      @Parameter(description = "집계 기간(1~30일)", example = "7")
          @RequestParam(defaultValue = "7")
          @Min(1)
          @Max(30)
          int periodDays,
      @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(1)
          int page,
      @Parameter(description = "페이지 크기(1~100)", example = "20")
          @RequestParam(defaultValue = "20")
          @Min(1)
          @Max(100)
          int size) {
    return ResponseEntity.ok(contentInteractionService.getPopularity(periodDays, page, size));
  }
}
