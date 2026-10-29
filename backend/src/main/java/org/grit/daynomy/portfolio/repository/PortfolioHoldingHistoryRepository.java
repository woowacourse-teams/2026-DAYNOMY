package org.grit.daynomy.portfolio.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingChangeType;
import org.grit.daynomy.portfolio.domain.PortfolioHoldingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PortfolioHoldingHistoryRepository
    extends JpaRepository<PortfolioHoldingHistory, Long> {

  @Query(
      "select min(h.effectiveDate) from PortfolioHoldingHistory h where h.portfolio.id = :portfolioId")
  Optional<LocalDate> findFirstEffectiveDateByPortfolioId(Long portfolioId);

  Optional<PortfolioHoldingHistory> findByIdAndPortfolioId(Long id, Long portfolioId);

  List<PortfolioHoldingHistory>
      findAllByPortfolioIdAndEffectiveDateBetweenOrderByEffectiveDateDescCreatedAtDescIdDesc(
          Long portfolioId, LocalDate from, LocalDate to);

  List<PortfolioHoldingHistory>
      findAllByPortfolioIdAndEffectiveDateLessThanEqualOrderByEffectiveDateAscCreatedAtAscIdAsc(
          Long portfolioId, LocalDate to);

  List<PortfolioHoldingHistory> findAllByPortfolioIdOrderByEffectiveDateAscCreatedAtAscIdAsc(
      Long portfolioId);

  boolean existsByPortfolioIdAndAssetIdAndChangeTypeInAndEffectiveDateAfter(
      Long portfolioId,
      Long assetId,
      Collection<PortfolioHoldingChangeType> changeTypes,
      LocalDate tradeDate);

  @Query(
      """
      select history from PortfolioHoldingHistory history join fetch history.asset
      where history.portfolio.id = :portfolioId
      order by history.effectiveDate, history.createdAt, history.id
      """)
  List<PortfolioHoldingHistory> findOrderedWithAssetsByPortfolioId(
      @Param("portfolioId") Long portfolioId);

  @Query(
      "select distinct h.asset.id from PortfolioHoldingHistory h where h.portfolio.member.id = :memberId")
  List<Long> findAssetIdsByMemberId(Long memberId);

  @Query(
      "select distinct h.asset.id from PortfolioHoldingHistory h where h.portfolio.id = :portfolioId")
  List<Long> findAssetIdsByPortfolioId(Long portfolioId);

  @Query(
      "select h.effectiveDate from PortfolioHoldingHistory h where h.id = :transactionId and h.portfolio.member.id = :memberId")
  Optional<LocalDate> findEffectiveDateByIdAndMemberId(Long transactionId, Long memberId);
}
