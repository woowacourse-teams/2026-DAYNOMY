package org.grit.daynomy.portfolio.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PortfolioHoldingRepository extends JpaRepository<PortfolioHolding, Long> {

  List<PortfolioHolding> findAllByPortfolioIdOrderById(Long portfolioId);

  Optional<PortfolioHolding> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);

  boolean existsByPortfolioMemberIdAndAssetId(Long memberId, Long assetId);

  List<PortfolioHolding> findAllByPortfolioMemberId(Long memberId);

  @Query(
      value =
          """
      WITH source AS (
        SELECT h.asset_id, h.quantity, h.average_purchase_price
        FROM portfolio_holdings h
        JOIN portfolios p ON p.id = h.portfolio_id
        JOIN assets a ON a.id = h.asset_id
        WHERE p.member_id = :memberId AND a.listed = TRUE AND a.category IN ('STOCK', 'ETF')
      ), shared AS (
        SELECT h.asset_id, h.quantity, h.average_purchase_price
        FROM shared_holdings h JOIN shared_portfolios p ON p.id = h.portfolio_id
        WHERE p.member_id = :memberId
      )
      SELECT EXISTS (SELECT 1 FROM portfolios WHERE member_id = :memberId)
        AND EXISTS (
          SELECT 1 FROM source s FULL JOIN shared h ON s.asset_id = h.asset_id
          WHERE s.asset_id IS NULL OR h.asset_id IS NULL
            OR s.quantity <> h.quantity OR s.average_purchase_price <> h.average_purchase_price
        )
      """,
      nativeQuery = true)
  boolean hasSharedPortfolioChanges(Long memberId);
}
