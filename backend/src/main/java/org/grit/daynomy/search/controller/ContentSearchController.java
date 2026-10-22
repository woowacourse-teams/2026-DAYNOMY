package org.grit.daynomy.search.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.search.domain.ContentSearchSort;
import org.grit.daynomy.search.domain.ContentSearchSource;
import org.grit.daynomy.search.dto.ContentSearchResponse;
import org.grit.daynomy.search.service.ContentSearchService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "통합 검색", description = "뉴스·주요 인물 게시물·종목 관련 자료 통합 검색 API")
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/api/search/contents")
public class ContentSearchController {

  private final ContentSearchService service;

  @Operation(
      summary = "소식 전체 검색",
      description =
          "발행 기사, 인물의 글, 저장된 YouTube·Threads·X 자료의 제목과 기사 본문을 검색합니다. "
              + "공백·중복 단어·대소문자를 정규화하고 모든 단어가 일치해야 합니다. "
              + "출처별 필터와 최신순(LATEST, 기본값)·인기순(POPULAR) 페이지를 반환합니다. "
              + "인기순은 한국 날짜 기준 최근 7일의 열람·펼치기 1점, 원문 클릭 3점을 합산하며 "
              + "반응이 없는 결과도 포함합니다. 점수가 같으면 최신순 기준으로 정렬합니다. "
              + "같은 URL의 자산 콘텐츠는 하나로 합치며 인물 글과 겹치면 인물 글을 우선합니다. "
              + "인기 점수는 중복 제거 후 노출되는 kind·ID에 기록된 반응을 기준으로 계산합니다. "
              + "기사 날짜는 발행일, 외부 콘텐츠 날짜는 등록일이며 동점은 종류·ID순입니다. "
              + "kind는 NEWS, PERSON_POST, ASSET_CONTENT 중 하나입니다.")
  @GetMapping
  public ContentSearchResponse search(
      @RequestParam(name = "q", required = false)
          @NotBlank(message = "검색어를 입력해주세요.")
          @Size(max = 100, message = "검색어는 100자 이하여야 합니다.")
          @Pattern(regexp = ".*[\\p{L}\\p{N}].*", message = "올바른 검색어를 입력해주세요.")
          String keyword,
      @RequestParam(defaultValue = "ALL") ContentSearchSource source,
      @RequestParam(defaultValue = "1") @Min(1) int page,
      @RequestParam(defaultValue = "12") @Min(1) @Max(100) int size,
      @RequestParam(defaultValue = "LATEST") ContentSearchSort sort) {
    return service.search(keyword, source, page, size, sort);
  }
}
