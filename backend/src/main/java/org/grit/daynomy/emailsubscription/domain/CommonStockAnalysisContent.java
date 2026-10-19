package org.grit.daynomy.emailsubscription.domain;

import java.util.List;
import org.grit.daynomy.market.domain.asset.ImpactDirection;
import org.grit.daynomy.market.domain.asset.ImpactLevel;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult.Source;

/** 종목 차원의 결과만 보관한다. 회원의 보유 정보·우선순위·전체 포트폴리오 설명은 포함하지 않는다. */
public record CommonStockAnalysisContent(
    ImpactDirection direction,
    ImpactLevel impactLevel,
    String issueSummary,
    String expectedReaction,
    String outlook,
    String reason,
    List<Source> sources) {

  public CommonStockAnalysisContent {
    sources = List.copyOf(sources);
  }
}
