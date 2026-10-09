package org.grit.daynomy.league.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.league.dto.LeagueDto.DecisionRecordRequest;
import org.grit.daynomy.league.dto.LeagueDto.FollowSummaryListResponse;
import org.grit.daynomy.league.dto.LeagueDto.ProfileRequest;
import org.grit.daynomy.league.dto.LeagueDto.ProfileResponse;
import org.grit.daynomy.league.dto.LeagueDto.PublicationRequest;
import org.grit.daynomy.league.dto.LeagueDto.ReviewRequest;
import org.grit.daynomy.league.dto.LeagueDto.ReviewResponse;
import org.grit.daynomy.league.dto.LeagueDto.TransactionListResponse;
import org.grit.daynomy.league.dto.LeagueDto.TransactionResponse;
import org.grit.daynomy.league.service.InvestorProfileService;
import org.grit.daynomy.league.service.LeagueService;
import org.grit.daynomy.league.service.PortfolioTransactionService;
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
  private final PortfolioTransactionService transactionService;
  private final LeagueService leagueService;

  @Operation(summary = "내 공개 투자자 프로필 조회")
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
  public ResponseEntity<TransactionListResponse> transactions(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(transactionService.getMine(member.memberId()));
  }

  @Operation(summary = "투자 판단 복기 추가")
  @PostMapping("/portfolio/decisions/{transactionId}/reviews")
  public ResponseEntity<ReviewResponse> addReview(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable Long transactionId,
      @Valid @RequestBody ReviewRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(transactionService.addReview(member.memberId(), transactionId, request));
  }

  @Operation(summary = "보유 자산의 판단 기록 (수량 변경 없음)")
  @PostMapping("/portfolio/decisions")
  public ResponseEntity<TransactionResponse> recordDecision(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody DecisionRecordRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(transactionService.recordDecision(member.memberId(), request));
  }

  @Operation(summary = "공개 투자자 팔로우")
  @PostMapping("/league/follows/{publicId}")
  public ResponseEntity<Void> follow(
      @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedMember member,
      @PathVariable String publicId) {
    leagueService.follow(member.memberId(), publicId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "공개 투자자 팔로우 해제")
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
