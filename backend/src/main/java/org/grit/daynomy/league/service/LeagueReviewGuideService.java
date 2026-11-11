package org.grit.daynomy.league.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.ai.LeagueReviewSearchAgent;
import org.grit.daynomy.league.ai.OpenAiLeagueReviewGuideClient;
import org.grit.daynomy.league.dto.LeagueDto.ReviewGuideResponse;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LeagueReviewGuideService {
  private final LeagueReviewGuideContextService contextService;
  private final LeagueReviewGuideRateLimiter limiter;
  private final OpenAiLeagueReviewGuideClient client;
  private final LeagueReviewSearchAgent searchAgent;

  public ReviewGuideResponse generate(Long memberId, Long transactionId) {
    var context = contextService.load(memberId, transactionId);
    limiter.acquire(memberId);
    try {
      var candidates = searchAgent.find(context.input(), context.assetId(), context.updatedAt());
      return new ReviewGuideResponse(
          transactionId,
          candidates.isEmpty() ? "NO_EVIDENCE" : "FOUND",
          candidates.stream().map(candidate -> candidate.evidence()).toList(),
          context.basis(),
          client.generate(context.input(), candidates));
    } catch (BusinessException exception) {
      throw exception;
    } catch (RuntimeException exception) {
      throw new BusinessException(LeagueErrorCode.REVIEW_GUIDE_UNAVAILABLE);
    } finally {
      limiter.release(memberId);
    }
  }
}
