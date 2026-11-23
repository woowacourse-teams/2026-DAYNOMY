package org.grit.daynomy.portfolio.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioAnalysisShareRepository
    extends JpaRepository<PortfolioAnalysisShare, Long> {

  Optional<PortfolioAnalysisShare> findByTokenHash(String tokenHash);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select share from PortfolioAnalysisShare share where share.tokenHash = :tokenHash")
  Optional<PortfolioAnalysisShare> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

  Optional<PortfolioAnalysisShare> findByIdAndOwnerId(Long id, Long ownerId);

  List<PortfolioAnalysisShare> findAllByOwnerIdOrderByCreatedAtDescIdDesc(Long ownerId);
}
