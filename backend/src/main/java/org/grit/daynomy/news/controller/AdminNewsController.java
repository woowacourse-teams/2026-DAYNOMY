package org.grit.daynomy.news.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.response.ErrorResponse;
import org.grit.daynomy.content.service.AssetContentService;
import org.grit.daynomy.news.domain.Category;
import org.grit.daynomy.news.domain.News;
import org.grit.daynomy.news.domain.NewsStatus;
import org.grit.daynomy.news.dto.AdminNewsCreateRequest;
import org.grit.daynomy.news.dto.AdminNewsGenerationResponse;
import org.grit.daynomy.news.dto.AdminNewsPageResponse;
import org.grit.daynomy.news.dto.AdminNewsResponse;
import org.grit.daynomy.news.dto.AdminNewsUpdateRequest;
import org.grit.daynomy.news.dto.WikimediaImageSearchResponse;
import org.grit.daynomy.news.service.AdminNewsService;
import org.grit.daynomy.news.service.NewsGenerationService;
import org.grit.daynomy.search.domain.NewsSearchSort;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Tag(name = "Admin News", description = "관리자 뉴스 관리 API")
@Validated
@RequiredArgsConstructor
@RequestMapping("/api/admin/news")
@RestController
public class AdminNewsController {

  private final AdminNewsService adminNewsService;
  private final NewsGenerationService newsGenerationService;
  private final AssetContentService assetContentService;

  @Operation(summary = "뉴스 등록", description = "관리자용 뉴스를 초안 상태로 등록합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "등록 성공",
        content = @Content(schema = @Schema(implementation = AdminNewsResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 또는 이미지 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "관련 자산을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AdminNewsResponse> createNews(
      @Valid @RequestPart("request") AdminNewsCreateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image) {
    News news = adminNewsService.createDraft(request, image);

    return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(news));
  }

  @Operation(summary = "한국 뉴스 초안 생성 실행", description = "한국 뉴스 초안 생성을 즉시 실행합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "생성 실행 성공",
      content = @Content(schema = @Schema(implementation = AdminNewsGenerationResponse.class)))
  @PostMapping("/generate/economy/korea")
  public ResponseEntity<AdminNewsGenerationResponse> generateKoreanEconomicNewsDrafts() {
    int savedCount = newsGenerationService.generateKoreanEconomicNewsDrafts().size();

    return ResponseEntity.ok(new AdminNewsGenerationResponse(savedCount));
  }

  @Operation(summary = "미국 뉴스 초안 생성 실행", description = "미국 뉴스 초안 생성을 즉시 실행합니다.")
  @ApiResponse(
      responseCode = "200",
      description = "생성 실행 성공",
      content = @Content(schema = @Schema(implementation = AdminNewsGenerationResponse.class)))
  @PostMapping("/generate/economy/us")
  public ResponseEntity<AdminNewsGenerationResponse> generateUsEconomicNewsDrafts() {
    int savedCount = newsGenerationService.generateUsEconomicNewsDrafts().size();

    return ResponseEntity.ok(new AdminNewsGenerationResponse(savedCount));
  }

  @Operation(
      summary = "Wikimedia Commons 이미지 검색",
      description = "라이선스가 허용된 Wikimedia Commons 이미지를 검색합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "검색 성공",
        content = @Content(schema = @Schema(implementation = WikimediaImageSearchResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "검색어 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/image-search")
  public ResponseEntity<WikimediaImageSearchResponse> searchWikimediaImages(
      @Parameter(description = "검색어(2~100자)", example = "한국은행")
          @RequestParam
          @jakarta.validation.constraints.Size(min = 2, max = 100)
          String keyword) {
    return ResponseEntity.ok(
        new WikimediaImageSearchResponse(adminNewsService.searchWikimediaImages(keyword.strip())));
  }

  @Operation(summary = "뉴스 이미지 생성", description = "초안 또는 발행된 뉴스의 이미지를 생성하거나 교체해 저장합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "이미지 생성 성공",
        content = @Content(schema = @Schema(implementation = AdminNewsResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "뉴스를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "이미지를 생성할 수 없는 뉴스 상태",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/{id}/generate-image")
  public ResponseEntity<AdminNewsResponse> generateNewsImage(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long id) {
    News news = adminNewsService.generateImage(id);

    return ResponseEntity.ok(toResponse(news));
  }

  @Operation(
      summary = "관리자 뉴스 목록 조회",
      description =
          "공백으로 구분한 모든 검색 단어가 제목 또는 본문에 포함된 뉴스를 상태·카테고리별로 조회합니다. "
              + "정렬은 기본 등록일 최신순(LATEST), 제목 일치 우선(RELEVANCE)을 지원합니다. "
              + "검색어 생략 또는 공백 입력 시 기존 등록일 최신순 목록을 반환합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = AdminNewsPageResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "검색 또는 페이지 조건 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping
  public ResponseEntity<AdminNewsPageResponse> getNewsPage(
      @Parameter(description = "1부터 시작하는 페이지 번호", example = "1")
          @RequestParam(defaultValue = "1")
          @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.")
          int page,
      @Parameter(description = "페이지 크기", example = "15")
          @RequestParam(defaultValue = "15")
          @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.")
          @Max(value = 100, message = "페이지 크기는 100 이하여야 합니다.")
          int size,
      @Parameter(description = "뉴스 상태. 생략하면 전체 조회", example = "DRAFT")
          @RequestParam(required = false)
          NewsStatus status,
      @Parameter(description = "뉴스 카테고리. 생략하면 전체 조회", example = "STOCK")
          @RequestParam(required = false)
          Category category,
      @Parameter(description = "제목·본문 검색어(최대 100자)", example = "금리")
          @RequestParam(name = "q", required = false)
          @Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
          String keyword,
      @Parameter(description = "정렬 방식: LATEST(등록일 최신순), RELEVANCE(제목 일치 우선)", example = "LATEST")
          @RequestParam(defaultValue = "LATEST")
          NewsSearchSort sort) {
    return ResponseEntity.ok(
        adminNewsService.getNewsPage(page, size, status, category, keyword, sort));
  }

  @Operation(summary = "관리자 뉴스 상세 조회", description = "관리자용으로 뉴스 상세 정보를 조회합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "조회 성공",
        content = @Content(schema = @Schema(implementation = AdminNewsResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "뉴스를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @GetMapping("/{id}")
  public ResponseEntity<AdminNewsResponse> getNewsDetail(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long id) {
    News news = adminNewsService.getNewsDetail(id);

    return ResponseEntity.ok(toResponse(news));
  }

  @Operation(summary = "뉴스 발행", description = "관리자용 초안 뉴스를 발행 상태로 변경합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "발행 성공",
        content = @Content(schema = @Schema(implementation = AdminNewsResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "뉴스를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "발행할 수 없는 뉴스 상태",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/{id}/publish")
  public ResponseEntity<AdminNewsResponse> publishNews(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long id) {
    News news = adminNewsService.publish(id);

    return ResponseEntity.ok(toResponse(news));
  }

  @Operation(summary = "뉴스 거절", description = "관리자용 초안 뉴스를 거절 상태로 변경합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "거절 성공",
        content = @Content(schema = @Schema(implementation = AdminNewsResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "뉴스를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "409",
        description = "거절할 수 없는 뉴스 상태",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PostMapping("/{id}/reject")
  public ResponseEntity<AdminNewsResponse> rejectNews(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long id) {
    News news = adminNewsService.reject(id);

    return ResponseEntity.ok(toResponse(news));
  }

  @Operation(summary = "뉴스 수정", description = "관리자용 뉴스 내용을 수정합니다.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "수정 성공",
        content = @Content(schema = @Schema(implementation = AdminNewsResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "요청 또는 이미지 형식 오류",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "뉴스 또는 관련 자산을 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<AdminNewsResponse> updateNews(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long id,
      @Valid @RequestPart("request") AdminNewsUpdateRequest request,
      @RequestPart(value = "image", required = false) MultipartFile image) {
    News news = adminNewsService.update(id, request, image);

    return ResponseEntity.ok(toResponse(news));
  }

  @Operation(summary = "뉴스 삭제", description = "관리자용 뉴스를 삭제 상태로 변경합니다.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "삭제 성공"),
    @ApiResponse(
        responseCode = "404",
        description = "뉴스를 찾을 수 없음",
        content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
  })
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> deleteNews(
      @Parameter(description = "뉴스 ID", example = "1") @PathVariable Long id) {
    adminNewsService.delete(id);

    return ResponseEntity.noContent().build();
  }

  private AdminNewsResponse toResponse(News news) {
    return AdminNewsResponse.from(news, assetContentService.getRelatedAssets(news.getId()));
  }
}
