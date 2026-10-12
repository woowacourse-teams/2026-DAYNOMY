package org.grit.daynomy.portfolio.repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioRepository extends JpaRepository<Portfolio, Long> {

  Optional<Portfolio> findByMemberId(Long memberId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select portfolio from Portfolio portfolio where portfolio.id = :id")
  Optional<Portfolio> findByIdForUpdate(@Param("id") Long id);
}
