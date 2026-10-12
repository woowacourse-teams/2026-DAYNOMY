package org.grit.daynomy.portfolio.repository;

import java.util.List;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PortfolioAnalysisEvidenceRepository
    extends JpaRepository<PortfolioAnalysisEvidence, Long> {

  List<PortfolioAnalysisEvidence> findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
      Long analysisId, Long memberId);
}
