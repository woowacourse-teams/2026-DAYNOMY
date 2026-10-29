package org.grit.daynomy.emailsubscription.service;

import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.repository.MarketTradingDayRepository;
import org.grit.daynomy.content.domain.AssetContent;
import org.grit.daynomy.content.domain.ContentSourceType;
import org.grit.daynomy.emailsubscription.domain.EmailDelivery;
import org.grit.daynomy.emailsubscription.domain.EmailDigestSupplement;
import org.grit.daynomy.emailsubscription.domain.EmailStockDigest;
import org.grit.daynomy.emailsubscription.repository.EmailDeliveryRepository;
import org.grit.daynomy.emailsubscription.repository.EmailStockContentRepository;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.grit.daynomy.portfolio.domain.PortfolioEvidenceComparison;
import org.grit.daynomy.portfolio.domain.PortfolioHolding;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetImpactResponse;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisEvidenceRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisRepository;
import org.grit.daynomy.portfolio.repository.PortfolioDailySnapshotRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingHistoryRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailDigestService {
  private static final List<ContentSourceType> TYPES =
      List.of(
          ContentSourceType.YOUTUBE,
          ContentSourceType.THREADS,
          ContentSourceType.X,
          ContentSourceType.INTERNAL_NEWS);
  private final ImportantStockSelectionService selection;
  private final PortfolioRepository portfolios;
  private final PortfolioHoldingRepository holdings;
  private final PortfolioHoldingHistoryRepository histories;
  private final PortfolioDailySnapshotRepository snapshots;
  private final MarketTradingDayRepository tradingDays;
  private final EmailStockContentRepository contents;
  private final EmailDeliveryRepository deliveries;
  private final PortfolioAnalysisRepository analyses;
  private final PortfolioAnalysisEvidenceRepository evidence;

  public record Prepared(EmailStockDigest stockDigest, EmailDigestSupplement supplement) {}

  public Optional<Prepared> compose(Long memberId, Instant cutoff) {
    var portfolio = portfolios.findByMemberId(memberId).orElse(null);
    if (portfolio == null) return Optional.empty();
    var positions = holdings.findAllByPortfolioIdOrderById(portfolio.getId());
    if (positions.isEmpty()) return Optional.empty();
    var quantities = quantities(positions);
    var byId =
        positions.stream()
            .map(PortfolioHolding::getAsset)
            .collect(Collectors.toMap(asset -> asset.getId(), Function.identity()));
    var date = cutoff.atZone(EmailDelivery.ZONE).toLocalDate();
    var selected = selection.select(memberId, date).orElse(null);
    var prices =
        selected == null
            ? List.<EmailStockDigest.Stock>of()
            : selected.stocks().stream()
                .filter(
                    stock -> byId.containsKey(stock.assetId()) && stock.dailyReturnRate() != null)
                .map(
                    stock ->
                        new EmailStockDigest.Stock(
                            stock.assetId(),
                            byId.get(stock.assetId()).getName(),
                            stock.dailyReturnRate(),
                            stock.weight(),
                            stock.contribution()))
                .toList();
    var coveredPrices =
        prices.isEmpty()
            ? List.<Long>of()
            : deliveries.findCoveredPriceAssetIds(
                memberId,
                selected.baseDate().toString(),
                prices.stream().map(EmailStockDigest.Stock::assetId).toList());
    prices = prices.stream().filter(stock -> !coveredPrices.contains(stock.assetId())).toList();
    var unique = new LinkedHashMap<String, EmailStockDigest.Content>();
    for (var item :
        contents.findRelatedCandidates(
            List.copyOf(quantities.keySet()),
            TYPES,
            cutoff.minus(java.time.Duration.ofDays(1)),
            cutoff))
      if (quantities.containsKey(item.getAsset().getId()) && validContent(item)) {
        var content = content(item);
        unique.putIfAbsent(content.key(), content);
      }
    var covered =
        unique.isEmpty()
            ? List.<String>of()
            : deliveries.findCoveredContentKeys(memberId, List.copyOf(unique.keySet()));
    var fresh =
        unique.values().stream().filter(item -> !covered.contains(item.key())).limit(3).toList();
    var assets = assets(portfolio, date);
    if (assets != null && deliveries.hasCoveredAssetSummary(memberId, assets.baseDate().toString()))
      assets = null;
    var analysis = analysis(memberId, cutoff, positions);
    if (analysis != null
        && deliveries.hasCoveredPortfolioAnalysis(memberId, analysis.id().toString()))
      analysis = null;
    var supplement = new EmailDigestSupplement(assets, analysis);
    var digest =
        EmailStockDigest.current(
            selected == null ? null : selected.baseDate(),
            selected == null ? null : selected.previousBaseDate(),
            quantities,
            prices,
            fresh);
    return digest.hasNewInformation() || supplement.shouldSend()
        ? Optional.of(new Prepared(digest, supplement))
        : Optional.empty();
  }

  /** 중복 안내 조회를 다시 적용하지 않고, 준비한 자료 자체의 유효성을 확인한다. */
  public boolean stillValid(
      Long memberId, Instant cutoff, EmailStockDigest prepared, EmailDigestSupplement supplement) {
    var portfolio = portfolios.findByMemberId(memberId).orElse(null);
    if (portfolio == null) return false;
    var positions = holdings.findAllByPortfolioIdOrderById(portfolio.getId());
    var currentHoldings = quantities(positions);
    if (!prepared.holdings().equals(currentHoldings)) return false;
    var date = cutoff.atZone(EmailDelivery.ZONE).toLocalDate();
    if (supplement.assets() != null
        && !Objects.equals(supplement.assets(), assets(portfolio, date))) return false;
    if (supplement.analysis() != null
        && !supplement.analysis().equals(analysis(memberId, cutoff, positions))) return false;
    if (!prepared.stocks().isEmpty()) {
      var current = selection.select(memberId, date).orElse(null);
      if (current == null
          || !Objects.equals(prepared.baseDate(), current.baseDate())
          || !Objects.equals(prepared.previousBaseDate(), current.previousBaseDate())) return false;
      for (var stock : prepared.stocks())
        if (current.stocks().stream()
            .noneMatch(
                item ->
                    item.assetId().equals(stock.assetId())
                        && Objects.equals(item.dailyReturnRate(), stock.dailyReturnRate())
                        && Objects.equals(item.weight(), stock.weight())
                        && Objects.equals(item.contribution(), stock.contribution()))) return false;
    }
    if (!prepared.contents().isEmpty()) {
      var current =
          contents
              .findCurrentByIds(
                  prepared.contents().stream().map(EmailStockDigest.Content::id).toList())
              .stream()
              .filter(this::validContent)
              .map(this::content)
              .toList();
      if (!current.containsAll(prepared.contents())) return false;
    }
    return true;
  }

  /** AI 호출 없이 저장된 신구 분석의 원문 근거만 비교한다. */
  private EmailDigestSupplement.Analysis analysis(
      Long memberId, Instant cutoff, List<PortfolioHolding> positions) {
    var current =
        analyses.findFirstByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(memberId).orElse(null);
    if (current == null
        || current.getPreviousAnalysisId() == null
        || current.getSummaryStatus() != PortfolioAnalysis.SummaryStatus.READY
        || !current.getAnalyzedAt().isBefore(cutoff)
        || current.getAnalyzedAt().isBefore(cutoff.minus(java.time.Duration.ofDays(1)))
        || !sameHoldings(current, positions)) return null;
    var previous =
        analyses
            .findByIdAndPortfolioMemberId(current.getPreviousAnalysisId(), memberId)
            .orElse(null);
    if (previous == null
        || !sameHoldings(previous, positions)
        || !previous.getAnalysisVersion().equals(current.getAnalysisVersion())
        || !previous.getModel().equals(current.getModel())) return null;
    var old =
        PortfolioAnalysisResponse.from(
            previous,
            evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                previous.getId(), memberId));
    var updated =
        PortfolioAnalysisResponse.from(
            current,
            evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                current.getId(), memberId));
    var names =
        updated.impacts().stream()
            .filter(item -> item.direction() != null)
            .filter(
                item ->
                    old.impacts().stream()
                        .anyMatch(
                            before ->
                                before.assetName().equals(item.assetName())
                                    && PortfolioEvidenceComparison.compare(
                                            facts(before), facts(item))
                                        == PortfolioEvidenceComparison.Result.NEW_EVIDENCE))
            .map(PortfolioAssetImpactResponse::assetName)
            .toList();
    return names.isEmpty()
        ? null
        : new EmailDigestSupplement.Analysis(current.getId(), current.getAnalyzedAt(), names);
  }

  private boolean sameHoldings(PortfolioAnalysis analysis, List<PortfolioHolding> positions) {
    var assets = analysis.getSnapshot().assets();
    return assets.size() == positions.size()
        && positions.stream().map(item -> item.getAsset().getName()).distinct().count()
            == positions.size()
        && assets.stream()
            .allMatch(
                asset ->
                    positions.stream()
                        .anyMatch(
                            position ->
                                position.getAsset().getName().equals(asset.assetName())
                                    && Objects.equals(position.getQuantity(), asset.quantity())));
  }

  private List<PortfolioEvidenceComparison.Fact> facts(PortfolioAssetImpactResponse impact) {
    return impact.sources().stream()
        .map(
            source ->
                new PortfolioEvidenceComparison.Fact(
                    source.url(), source.occurredOn(), source.excerpt(), source.factQuote()))
        .toList();
  }

  private Map<Long, Long> quantities(List<PortfolioHolding> positions) {
    return positions.stream()
        .collect(
            Collectors.toMap(
                position -> position.getAsset().getId(), PortfolioHolding::getQuantity));
  }

  private EmailDigestSupplement.Assets assets(Portfolio portfolio, LocalDate date) {
    var day =
        tradingDays
            .findFirstByTradingTrueAndCollectedAtIsNotNullAndBaseDateLessThanEqualOrderByBaseDateDesc(
                date.minusDays(1));
    if (day.isEmpty()) return null;
    var base = day.get().getBaseDate();
    if (!histories
        .findAllByPortfolioIdAndEffectiveDateBetweenOrderByEffectiveDateDescCreatedAtDescIdDesc(
            portfolio.getId(), base.plusDays(1), date)
        .isEmpty()) return null;
    var snapshot = snapshots.findByPortfolioIdAndBaseDate(portfolio.getId(), base).orElse(null);
    if (snapshot == null
        || snapshot.getDailyProfitLoss() == null
        || snapshot.getDailyReturnRate() == null
        || snapshot.getTotalEvaluationAmount().subtract(snapshot.getDailyProfitLoss()).signum()
            <= 0) return null;
    return new EmailDigestSupplement.Assets(
        base,
        snapshot.getTotalEvaluationAmount(),
        snapshot.getDailyProfitLoss(),
        snapshot.getDailyReturnRate());
  }

  private boolean validContent(AssetContent item) {
    if (!TYPES.contains(item.getSourceType())
        || item.getTitle() == null
        || item.getTitle().isBlank()
        || item.getUrl() == null
        || item.getCreatedAt() == null
        || (item.getNews() != null && !item.getNews().isPublished())) return false;
    if (item.getSourceType() == ContentSourceType.INTERNAL_NEWS)
      return item.getNews() != null
          && item.getNews().getPublishedAt() != null
          && item.getUrl().matches("/news/[1-9][0-9]*");
    try {
      var uri = URI.create(item.getUrl());
      return "https".equalsIgnoreCase(uri.getScheme())
          && uri.getHost() != null
          && uri.getUserInfo() == null;
    } catch (IllegalArgumentException exception) {
      return false;
    }
  }

  private EmailStockDigest.Content content(AssetContent item) {
    String key = item.getSourceType() + "|" + URI.create(item.getUrl()).normalize();
    return new EmailStockDigest.Content(
        item.getId(),
        key,
        item.getAsset().getId(),
        item.getAsset().getName(),
        item.getSourceType(),
        item.getTitle(),
        item.getUrl(),
        item.getNews() == null ? item.getCreatedAt() : item.getNews().getPublishedAt());
  }
}
