package org.grit.daynomy.portfolio.service;

import java.util.function.Supplier;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

@SessionScope
@Component
public class GuestPortfolioAnalysisTrial {

  private boolean consumed;

  public synchronized <T> T use(Supplier<T> analysis) {
    if (consumed) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_TRIAL_EXHAUSTED);
    }

    T result = analysis.get();
    consumed = true;
    return result;
  }
}
