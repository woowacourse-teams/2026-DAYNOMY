package org.grit.daynomy.league.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.league.dto.LeagueDto.DecisionListResponse;
import org.grit.daynomy.league.dto.LeagueDto.DecisionReasonRequest;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRecordRequest;
import org.grit.daynomy.league.dto.LeagueDto.DecisionResponse;
import org.grit.daynomy.league.dto.LeagueDto.FollowSummaryListResponse;
import org.grit.daynomy.league.dto.LeagueDto.ProfileRequest;
import org.grit.daynomy.league.dto.LeagueDto.ProfileResponse;
import org.grit.daynomy.league.dto.LeagueDto.PublicationRequest;
import org.grit.daynomy.league.dto.LeagueDto.ReviewRequest;
import org.grit.daynomy.league.dto.LeagueDto.ReviewResponse;
import org.grit.daynomy.league.service.InvestmentDecisionService;
import org.grit.daynomy.league.service.InvestorProfileService;
import org.grit.daynomy.league.service.LeagueService;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "My Investment League", description = "로그인 회원의 투자 리그 관리 API")
@RequiredArgsConstructor
@RequestMapping("/api/users/me")
@RestController
public class MyLeagueController {

  private final InvestorProfileService profileService;
  private final InvestmentDecisionService decisionService;
  private final LeagueService leagueService;

  @Operation(summary = "내 공개 투자자 프로필 조회")
  @ApiResponse(responseCode = "200", description = "내 공개 투자자 프로필 조회 성공")
  @ApiResponse(responseCode = "204", description = "아직 생성한 프로필 없음", content = @Content)
  @GetMapping("/investor-profile")
  public ResponseEntity<ProfileResponse> profile(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    ProfileResponse profile = profileService.getMine(member.memberId());
    return profile == null
        ? ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()
        : ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(profile);
  }

  @Operation(summary = "내 공개 투자자 프로필 저장")
  @PutMapping("/investor-profile")
  public ProfileResponse saveProfile(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody ProfileRequest request) {
    return profileService.save(member.memberId(), request);
  }

  @Operation(summary = "내 포트폴리오 공개 범위 저장")
  @PutMapping("/portfolio/publication")
  public ProfileResponse publish(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PublicationRequest request) {
    return profileService.publish(member.memberId(), request);
  }

  @Operation(summary = "내 거래와 투자 판단 목록 조회")
  @GetMapping("/portfolio/transactions")
  public ResponseEntity<DecisionListResponse> transactions(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(decisionService.getMine(member.memberId()));
  }

  @Operation(summary = "투자 판단 복기 추가")
  @ApiResponse(responseCode = "201", description = "복기 생성 성공")
  @PostMapping("/portfolio/decisions/{transactionId}/reviews")
  public ResponseEntity<ReviewResponse> addReview(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long transactionId,
      @Valid @RequestBody ReviewRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(decisionService.addReview(member.memberId(), transactionId, request));
  }

  @Operation(summary = "내 투자 판단 복기 수정")
  @PutMapping("/portfolio/decisions/{transactionId}/reviews/{reviewId}")
  public ResponseEntity<ReviewResponse> reviseReview(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long transactionId,
      @PathVariable Long reviewId,
      @Valid @RequestBody ReviewRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(decisionService.reviseReview(member.memberId(), transactionId, reviewId, request));
  }

  @Operation(summary = "보유 자산의 판단 기록 (수량 변경 없음)")
  @ApiResponse(responseCode = "201", description = "판단 기록 생성 또는 동일 요청 키의 기존 기록 반환")
  @PostMapping("/portfolio/decisions")
  public ResponseEntity<DecisionResponse> recordDecision(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody DecisionRecordRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(decisionService.recordDecision(member.memberId(), request));
  }

  @Operation(summary = "내 투자 기록의 보유 이유 수정 (수량·가격·복기 유지)")
  @PutMapping("/portfolio/decisions/{transactionId}/reason")
  public ResponseEntity<DecisionResponse> reviseReason(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long transactionId,
      @Valid @RequestBody DecisionReasonRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(decisionService.reviseReason(member.memberId(), transactionId, request));
  }

  @Operation(summary = "공개 투자자 팔로우")
  @ApiResponse(responseCode = "204", description = "팔로우 완료", content = @Content)
  @PostMapping("/league/follows/{publicId}")
  public ResponseEntity<Void> follow(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable String publicId) {
    leagueService.follow(member.memberId(), publicId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "공개 투자자 팔로우 해제")
  @ApiResponse(responseCode = "204", description = "팔로우 해제 완료", content = @Content)
  @DeleteMapping("/league/follows/{publicId}")
  public ResponseEntity<Void> unfollow(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable String publicId) {
    leagueService.unfollow(member.memberId(), publicId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "팔로우한 투자자 주간 요약")
  @GetMapping("/league/follows/summary")
  public ResponseEntity<FollowSummaryListResponse> followSummary(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(leagueService.followSummary(member.memberId()));
  }
}
