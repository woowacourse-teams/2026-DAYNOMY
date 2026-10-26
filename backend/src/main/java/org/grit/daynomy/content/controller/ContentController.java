package org.grit.daynomy.content.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.content.dto.ContentPageResponse;
import org.grit.daynomy.content.service.AssetContentService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Content", description = "공개 외부 콘텐츠 조회 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/contents")
@RestController
public class ContentController {
  private final AssetContentService service;

  @Operation(
      summary = "소식의 공개 외부 자료 목록",
      description = "저장된 YouTube·Threads·X 자료를 원문 게시일, 등록일, ID 내림차순으로 조회합니다. 페이지는 1부터 시작합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = ContentPageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "페이지 조건 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public ContentPageResponse getContents(
      @Parameter(description = "페이지 번호(1부터 시작)", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(1)
          int page,
      @Parameter(description = "페이지 크기(1~100)", example = "12")
          @RequestParam(defaultValue = "12")
          @Min(1)
          @Max(100)
          int size) {
    return service.getPublicContents(page, size);
  }
}
