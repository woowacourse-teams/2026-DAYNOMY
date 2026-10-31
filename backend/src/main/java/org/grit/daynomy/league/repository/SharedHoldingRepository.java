package org.grit.daynomy.league.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.league.domain.SharedHolding;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SharedHoldingRepository extends JpaRepository<SharedHolding, Long> {
  @EntityGraph(attributePaths = "asset")
  List<SharedHolding> findAllByPortfolioIdOrderById(Long portfolioId);

  Optional<SharedHolding> findByPortfolioIdAndAssetId(Long portfolioId, Long assetId);

  @Query(
      value =
          """
      SELECT EXISTS (
        SELECT 1 FROM shared_holdings h
        JOIN shared_portfolios sp ON sp.id = h.portfolio_id
        JOIN public_investor_profiles p ON p.member_id = sp.member_id
        JOIN assets a ON a.id = h.asset_id
        WHERE p.league_enabled = TRUE
          AND a.market IN ('NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS')
      )
      """,
      nativeQuery = true)
  boolean existsActiveUsLeagueHolding();

  @Query(
      value =
          """
      SELECT DISTINCT sp.member_id FROM shared_holdings h
      JOIN shared_portfolios sp ON sp.id = h.portfolio_id
      JOIN public_investor_profiles p ON p.member_id = sp.member_id
      JOIN assets a ON a.id = h.asset_id
      WHERE p.league_enabled = TRUE
        AND a.market IN ('NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS')
      """,
      nativeQuery = true)
  List<Long> findActiveUsLeagueMemberIds();

  @Query(
      value =
          """
      SELECT DISTINCT h.asset_id FROM shared_holdings h
      JOIN shared_portfolios sp ON sp.id = h.portfolio_id
      JOIN public_investor_profiles p ON p.member_id = sp.member_id
      JOIN assets a ON a.id = h.asset_id
      WHERE p.league_enabled = TRUE
        AND a.market IN ('NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS')
      """,
      nativeQuery = true)
  List<Long> findActiveUsLeagueAssetIds();

  @Query(
      value =
          """
      SELECT EXISTS (
        SELECT 1 FROM shared_holdings h
        JOIN shared_portfolios sp ON sp.id = h.portfolio_id
        JOIN public_investor_profiles p ON p.member_id = sp.member_id
        JOIN assets a ON a.id = h.asset_id
        WHERE p.league_enabled = TRUE
          AND a.market IN ('KOSPI', 'KOSDAQ')
      )
      """,
      nativeQuery = true)
  boolean existsActiveDomesticLeagueHolding();

  @Query(
      value =
          """
      SELECT DISTINCT sp.member_id FROM shared_holdings h
      JOIN shared_portfolios sp ON sp.id = h.portfolio_id
      JOIN public_investor_profiles p ON p.member_id = sp.member_id
      JOIN assets a ON a.id = h.asset_id
      WHERE p.league_enabled = TRUE
        AND a.market IN ('KOSPI', 'KOSDAQ')
      """,
      nativeQuery = true)
  List<Long> findActiveDomesticLeagueMemberIds();

  @Query(
      value =
          """
      SELECT EXISTS (
        SELECT 1 FROM shared_holdings h JOIN shared_portfolios sp ON sp.id = h.portfolio_id
        JOIN assets a ON a.id = h.asset_id
        WHERE sp.member_id = :memberId
          AND a.market IN ('NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS')
      ) AND NOT EXISTS (
        SELECT 1 FROM shared_holdings h JOIN shared_portfolios sp ON sp.id = h.portfolio_id
        JOIN assets a ON a.id = h.asset_id
        WHERE sp.member_id = :memberId
          AND a.market IN ('KOSPI', 'KOSDAQ')
      )
      """,
      nativeQuery = true)
  boolean hasOnlyUsHoldings(Long memberId);

  @Query(
      value =
          """
      SELECT EXISTS (
        SELECT 1 FROM shared_holdings h JOIN shared_portfolios sp ON sp.id = h.portfolio_id
        JOIN assets a ON a.id = h.asset_id
        WHERE sp.member_id = :memberId
          AND a.market IN ('NASDAQ', 'NYSE', 'NYSE_ARCA', 'NYSE_AMERICAN', 'BATS')
      )
      """,
      nativeQuery = true)
  boolean hasActiveUsHolding(Long memberId);
}
