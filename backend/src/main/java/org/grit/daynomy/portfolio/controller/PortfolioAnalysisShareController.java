package org.grit.daynomy.portfolio.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisShareDto;
import org.grit.daynomy.portfolio.dto.PortfolioReviewProposalDto;
import org.grit.daynomy.portfolio.service.PortfolioAnalysisShareService;
import org.grit.daynomy.portfolio.service.PortfolioReviewProposalService;
import org.grit.daynomy.portfolio.service.PortfolioReviewSimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@Validated
@Tag(name = "PortfolioAnalysisShare", description = "포트폴리오 분석 의견 요청 API")
@RequiredArgsConstructor
@RestController
public class PortfolioAnalysisShareController {

  private static final String SHARE_TOKEN_HEADER = "X-Portfolio-Share-Token";

  private final PortfolioAnalysisShareService service;
  private final PortfolioReviewSimulationService simulation;
  private final PortfolioReviewProposalService proposals;

  @Operation(summary = "저장 검토안 목록 조회 (작성자 전용, 공유 종료 후에도 가능)")
  @GetMapping("/api/users/me/portfolio/analysis/shares/{shareId}/proposals")
  public List<PortfolioReviewProposalDto.Response> proposals(
      @AuthenticationPrincipal AuthenticatedMember member, @Positive @PathVariable Long shareId) {
    return proposals.list(member.memberId(), shareId);
  }

  @Operation(summary = "댓글 제안을 반영한 검토안 저장 또는 갱신 (활성 공유 작성자 전용)")
  @PutMapping("/api/users/me/portfolio/analysis/shares/{shareId}/comments/{commentId}/proposal")
  public PortfolioReviewProposalDto.Response saveProposal(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long shareId,
      @Positive @PathVariable Long commentId,
      @Valid @RequestBody PortfolioReviewProposalDto.Request request) {
    return proposals.save(member.memberId(), shareId, commentId, request);
  }

  @Operation(summary = "저장 검토안 폐기 (작성자 전용, 공유 종료 후에도 가능)")
  @DeleteMapping("/api/users/me/portfolio/analysis/shares/{shareId}/comments/{commentId}/proposal")
  public ResponseEntity<Void> deleteProposal(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long shareId,
      @Positive @PathVariable Long commentId) {
    proposals.delete(member.memberId(), shareId, commentId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "댓글 제안 검토용 현재 자산·원화 평가금액 조회 (작성자 전용)")
  @GetMapping("/api/users/me/portfolio/analysis/shares/{shareId}/comments/{commentId}/simulation")
  public List<PortfolioAnalysisShareDto.SimulationAssetResponse> simulationAssets(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long shareId,
      @Positive @PathVariable Long commentId) {
    return service.simulationAssets(member.memberId(), shareId, commentId);
  }

  @Operation(summary = "댓글 제안을 반영한 임시 포트폴리오 분석 (보유 내역·저장된 분석 변경 없음)")
  @PostMapping("/api/users/me/portfolio/analysis/shares/{shareId}/comments/{commentId}/simulation")
  public PortfolioAnalysisResponse simulate(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long shareId,
      @Positive @PathVariable Long commentId,
      @Valid @RequestBody PortfolioAnalysisRequest request) {
    return simulation.analyze(member.memberId(), shareId, commentId, request);
  }

  @Operation(summary = "내 분석 결과 공유 링크 발급 또는 재발급")
  @PostMapping("/api/users/me/portfolio/analysis/shares")
  public PortfolioAnalysisShareDto.CreatedResponse create(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PortfolioAnalysisShareDto.CreateRequest request) {
    return service.create(member.memberId(), request);
  }

  @Operation(summary = "내 분석 결과 의견 요청 목록 조회")
  @GetMapping("/api/users/me/portfolio/analysis/shares")
  public List<PortfolioAnalysisShareDto.OwnerResponse> list(
      @AuthenticationPrincipal AuthenticatedMember member) {
    return service.list(member.memberId());
  }

  @Operation(summary = "내 분석 결과 공유 중단")
  @PostMapping("/api/users/me/portfolio/analysis/shares/{shareId}/revoke")
  public PortfolioAnalysisShareDto.OwnerResponse revoke(
      @AuthenticationPrincipal AuthenticatedMember member, @Positive @PathVariable Long shareId) {
    return service.revoke(member.memberId(), shareId);
  }

  @Operation(summary = "공유 링크의 공개 추가 설명 수정 (작성자 전용, 빈 내용은 설명 내리기)")
  @PutMapping("/api/users/me/portfolio/analysis/shares/{shareId}/explanation")
  public PortfolioAnalysisShareDto.OwnerResponse updateExplanation(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long shareId,
      @Valid @RequestBody PortfolioAnalysisShareDto.ExplanationRequest request) {
    return service.updateExplanation(member.memberId(), shareId, request);
  }

  @Operation(summary = "내 분석 결과 의견 요청과 받은 의견 영구 삭제")
  @DeleteMapping("/api/users/me/portfolio/analysis/shares/{shareId}")
  public ResponseEntity<Void> delete(
      @AuthenticationPrincipal AuthenticatedMember member, @Positive @PathVariable Long shareId) {
    service.delete(member.memberId(), shareId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "공유된 분석 결과 조회")
  @GetMapping("/api/portfolio/analysis/shares")
  public ResponseEntity<PortfolioAnalysisShareDto.PublicResponse> getPublic(
      @Size(max = 100) @RequestHeader(SHARE_TOKEN_HEADER) String token,
      @AuthenticationPrincipal AuthenticatedMember member,
      HttpServletRequest request) {
    return ResponseEntity.ok()
        .header("Referrer-Policy", "no-referrer")
        .header("X-Robots-Tag", "noindex, nofollow")
        .body(
            service.getPublic(
                token, member == null ? null : member.memberId(), request.getRemoteAddr()));
  }

  @Operation(summary = "공유된 분석 결과에 댓글 작성")
  @PostMapping("/api/portfolio/analysis/shares/comments")
  public PortfolioAnalysisShareDto.CommentResponse createComment(
      @Size(max = 100) @RequestHeader(SHARE_TOKEN_HEADER) String token,
      @AuthenticationPrincipal AuthenticatedMember member,
      @Valid @RequestBody PortfolioAnalysisShareDto.CommentRequest request) {
    return service.createComment(token, member.memberId(), request);
  }

  @Operation(summary = "공유된 분석 결과에서 내 댓글 수정")
  @PutMapping("/api/portfolio/analysis/shares/comments/{commentId}")
  public PortfolioAnalysisShareDto.CommentResponse updateComment(
      @Size(max = 100) @RequestHeader(SHARE_TOKEN_HEADER) String token,
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long commentId,
      @Valid @RequestBody PortfolioAnalysisShareDto.CommentRequest request) {
    return service.updateComment(token, member.memberId(), commentId, request);
  }

  @Operation(summary = "공유된 분석 결과에서 내 댓글 삭제")
  @DeleteMapping("/api/portfolio/analysis/shares/comments/{commentId}")
  public ResponseEntity<Void> deleteComment(
      @Size(max = 100) @RequestHeader(SHARE_TOKEN_HEADER) String token,
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long commentId) {
    service.deleteComment(token, member.memberId(), commentId);
    return ResponseEntity.noContent().build();
  }

  @Operation(summary = "받은 댓글에 작성자 대댓글 작성")
  @PostMapping("/api/users/me/portfolio/analysis/shares/{shareId}/comments/{commentId}/replies")
  public PortfolioAnalysisShareDto.CommentResponse saveOwnerReply(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long shareId,
      @Positive @PathVariable Long commentId,
      @Valid @RequestBody PortfolioAnalysisShareDto.CommentRequest request) {
    return service.saveOwnerReply(member.memberId(), shareId, commentId, request);
  }

  @Operation(summary = "내 댓글에 대댓글 작성")
  @PostMapping("/api/portfolio/analysis/shares/comments/{commentId}/replies")
  public PortfolioAnalysisShareDto.CommentResponse createCommenterReply(
      @Size(max = 100) @RequestHeader(SHARE_TOKEN_HEADER) String token,
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long commentId,
      @Valid @RequestBody PortfolioAnalysisShareDto.CommentRequest request) {
    return service.createCommenterReply(token, member.memberId(), commentId, request);
  }

  @Operation(summary = "내 대댓글 수정")
  @PutMapping("/api/users/me/portfolio/analysis/comment-replies/{replyId}")
  public PortfolioAnalysisShareDto.CommentResponse updateReply(
      @AuthenticationPrincipal AuthenticatedMember member,
      @Positive @PathVariable Long replyId,
      @Valid @RequestBody PortfolioAnalysisShareDto.CommentRequest request) {
    return service.updateReply(member.memberId(), replyId, request);
  }

  @Operation(summary = "내 대댓글 삭제")
  @DeleteMapping("/api/users/me/portfolio/analysis/comment-replies/{replyId}")
  public PortfolioAnalysisShareDto.CommentResponse deleteReply(
      @AuthenticationPrincipal AuthenticatedMember member, @Positive @PathVariable Long replyId) {
    return service.deleteReply(member.memberId(), replyId);
  }
}
