package org.grit.daynomy.league.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.ai.LeagueReviewSearchAgent;
import org.grit.daynomy.league.ai.OpenAiLeagueReviewGuideClient;
import org.grit.daynomy.league.ai.OpenAiLeagueReviewGuideClient.Input;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.junit.jupiter.api.Test;

class LeagueReviewGuideServiceTest {
  @Test
  void releasesTheLimitWhenAiFails() {
    var context = mock(LeagueReviewGuideContextService.class);
    var limiter = mock(LeagueReviewGuideRateLimiter.class);
    var client = mock(OpenAiLeagueReviewGuideClient.class);
    var search = mock(LeagueReviewSearchAgent.class);
    var input = new Input("예시 종목", "HOLD", "이유", "", "", null, null, true);
    given(context.load(1L, 7L))
        .willReturn(
            new LeagueReviewGuideContextService.Context(input, null, 2L, java.time.Instant.now()));
    given(
            search.find(
                org.mockito.ArgumentMatchers.eq(input),
                org.mockito.ArgumentMatchers.eq(2L),
                org.mockito.ArgumentMatchers.any()))
        .willReturn(java.util.List.of());
    given(client.generate(input, java.util.List.of()))
        .willThrow(new BusinessException(LeagueErrorCode.REVIEW_GUIDE_UNAVAILABLE));

    assertThatThrownBy(
            () -> new LeagueReviewGuideService(context, limiter, client, search).generate(1L, 7L))
        .isInstanceOf(BusinessException.class);
    verify(limiter).release(1L);
  }
}
