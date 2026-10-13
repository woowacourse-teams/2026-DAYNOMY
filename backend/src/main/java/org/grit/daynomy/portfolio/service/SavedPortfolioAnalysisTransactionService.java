package org.grit.daynomy.portfolio.service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.portfolio.domain.Portfolio;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysis;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisCheck;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisEvidence;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSnapshot;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisRequest;
import org.grit.daynomy.portfolio.dto.PortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.PortfolioAssetRequest;
import org.grit.daynomy.portfolio.dto.PortfolioCalculateRequest;
import org.grit.daynomy.portfolio.dto.PortfolioHoldingRequest;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse;
import org.grit.daynomy.portfolio.dto.SavedPortfolioAnalysisResponse.Status;
import org.grit.daynomy.portfolio.exception.PortfolioErrorCode;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisCheckRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisEvidenceRepository;
import org.grit.daynomy.portfolio.repository.PortfolioAnalysisRepository;
import org.grit.daynomy.portfolio.repository.PortfolioHoldingRepository;
import org.grit.daynomy.portfolio.repository.PortfolioRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SavedPortfolioAnalysisTransactionService {

  private static final String ANALYSIS_VERSION = "portfolio-v3";
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
  private static final int RETAINED_ANALYSES = 10;

  private final PortfolioRepository portfolios;
  private final PortfolioHoldingRepository holdings;
  private final PortfolioAnalysisRepository analyses;
  private final PortfolioAnalysisEvidenceRepository evidence;
  private final PortfolioAnalysisCheckRepository checks;
  private final PortfolioCalculationService calculation;
  private final String model;

  public SavedPortfolioAnalysisTransactionService(
      PortfolioRepository portfolios,
      PortfolioHoldingRepository holdings,
      PortfolioAnalysisRepository analyses,
      PortfolioAnalysisEvidenceRepository evidence,
      PortfolioAnalysisCheckRepository checks,
      PortfolioCalculationService calculation,
      @Value("${ai.openai.model:gpt-5-mini}") String model) {
    this.portfolios = portfolios;
    this.holdings = holdings;
    this.analyses = analyses;
    this.evidence = evidence;
    this.checks = checks;
    this.calculation = calculation;
    this.model = model;
  }

  @Transactional(readOnly = true)
  public SavedPortfolioAnalysisResponse get(Long memberId) {
    return portfolios
        .findByMemberId(memberId)
        .map(
            portfolio ->
                response(
                    memberId,
                    latest(memberId),
                    checks.findById(portfolio.getId()).orElse(null),
                    currentAssets(portfolio),
                    Instant.now()))
        .orElseGet(SavedPortfolioAnalysisResponse::empty);
  }

  @Transactional(readOnly = true)
  public List<org.grit.daynomy.portfolio.dto.PortfolioAnalysisHistoryResponse> history(
      Long memberId) {
    return analyses
        .findAllByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(
            memberId, org.springframework.data.domain.PageRequest.of(0, RETAINED_ANALYSES))
        .stream()
        .map(
            analysis ->
                new org.grit.daynomy.portfolio.dto.PortfolioAnalysisHistoryResponse(
                    analysis.getId(),
                    analysis.getPreviousAnalysisId(),
                    analysis.getAnalyzedAt(),
                    analysis.getChangeReason(),
                    PortfolioAnalysisResponse.from(
                        analysis,
                        evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                            analysis.getId(), memberId))))
        .toList();
  }

  @Transactional
  public PreparedAnalysis prepare(Long memberId, PortfolioAnalysisRequest request) {
    return prepare(memberId, request, false);
  }

  @Transactional
  public PreparedAnalysis prepareRefresh(Long memberId) {
    return prepare(memberId, null, true);
  }

  private PreparedAnalysis prepare(
      Long memberId, PortfolioAnalysisRequest request, boolean refresh) {
    Portfolio portfolio = lockedPortfolio(memberId);
    List<PortfolioAssetRequest> assets = currentAssets(portfolio);
    if (assets.isEmpty()) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_UNAVAILABLE);
    }
    // 비회원 브라우저에 남은 자산을 회원 분석으로 저장하지 않는다.
    if (request != null && !sameAssets(request.assets(), assets)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_CHANGED);
    }
    PortfolioAnalysis previous = latest(memberId);
    PortfolioAnalysisCheck check =
        checks
            .findById(portfolio.getId())
            .orElseGet(() -> checks.save(new PortfolioAnalysisCheck(portfolio)));
    Instant now = Instant.now();
    if (check.isProcessing(now)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_IN_PROGRESS);
    }
    if (!refresh
        && previous != null
        && matches(previous, assets)
        && ANALYSIS_VERSION.equals(previous.getAnalysisVersion())
        && model.equals(previous.getModel())) {
      return new PreparedAnalysis(
          response(memberId, previous, check, assets, now), null, assets, null);
    }
    UUID token = UUID.randomUUID();
    if (!check.begin(token, now, REQUEST_TIMEOUT)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_IN_PROGRESS);
    }
    return new PreparedAnalysis(
        null,
        token,
        assets,
        previous != null
                && matches(previous, assets)
                && ANALYSIS_VERSION.equals(previous.getAnalysisVersion())
                && model.equals(previous.getModel())
            ? PortfolioAnalysisResponse.from(
                previous,
                evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                    previous.getId(), memberId))
            : null);
  }

  @Transactional
  public SavedPortfolioAnalysisResponse complete(
      Long memberId,
      UUID token,
      List<PortfolioAssetRequest> assets,
      PortfolioAnalysisResponse result) {
    Portfolio portfolio = lockedPortfolio(memberId);
    PortfolioAnalysisCheck check = checks.findById(portfolio.getId()).orElseThrow();
    Instant now = Instant.now();
    if (!check.ownsRequest(token, now)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_REQUEST_EXPIRED);
    }
    if (!sameAssets(assets, currentAssets(portfolio))) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_CHANGED);
    }
    PortfolioAnalysis previous = latest(memberId);
    String reason =
        previous == null ? "최초 분석" : matches(previous, assets) ? "분석 기준 변경" : "보유 자산 또는 비중 변경";
    if (previous != null
        && matches(previous, assets)
        && ANALYSIS_VERSION.equals(previous.getAnalysisVersion())
        && model.equals(previous.getModel())) {
      var old =
          PortfolioAnalysisResponse.from(
              previous,
              evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                  previous.getId(), memberId));
      var revision = PortfolioAnalysisRefresh.compare(old, result);
      if (revision.outcome() != PortfolioAnalysisCheck.Outcome.UPDATED) {
        check.recordSuccess(now, revision.outcome());
        check.finish(token);
        return response(memberId, previous, check, assets, now);
      }
      result = revision.analysis();
      reason = revision.reason();
    }
    PortfolioAnalysis analysis =
        analyses.save(
            PortfolioAnalysis.create(
                portfolio,
                previous,
                ANALYSIS_VERSION,
                model,
                result.toSnapshot(assets),
                result.analyzedAt(),
                reason));
    List<PortfolioAnalysisEvidence> savedEvidence = new ArrayList<>();
    result
        .impacts()
        .forEach(
            impact ->
                impact.sources().stream()
                    .distinct()
                    .forEach(
                        source ->
                            savedEvidence.add(
                                new PortfolioAnalysisEvidence(
                                    analysis,
                                    impact.assetName(),
                                    source.title(),
                                    source.url(),
                                    source.publishedOn(),
                                    source.occurredOn(),
                                    source.excerpt(),
                                    source.factQuote()))));
    var assetSourceUrls =
        savedEvidence.stream()
            .map(PortfolioAnalysisEvidence::getUrl)
            .collect(java.util.stream.Collectors.toSet());
    result.sources().stream()
        .filter(source -> !assetSourceUrls.contains(source.url()))
        .distinct()
        .forEach(
            source ->
                savedEvidence.add(
                    new PortfolioAnalysisEvidence(
                        analysis,
                        null,
                        source.title(),
                        source.url(),
                        source.publishedOn(),
                        source.occurredOn(),
                        source.excerpt(),
                        source.factQuote())));
    evidence.saveAll(savedEvidence);
    check.recordSuccess(
        now,
        result.analyzedAssetCount() == 0
            ? PortfolioAnalysisCheck.Outcome.UNCONFIRMED
            : PortfolioAnalysisCheck.Outcome.UPDATED);
    check.finish(token);
    analyses.flush();
    analyses.deleteOlderAnalyses(portfolio.getId(), RETAINED_ANALYSES);
    return response(memberId, analysis, check, assets, now);
  }

  @Transactional
  public void fail(Long memberId, UUID token, String code) {
    Portfolio portfolio = lockedPortfolio(memberId);
    checks
        .findById(portfolio.getId())
        .ifPresent(
            check -> {
              // 만료 후 새 작업이 시작됐다면 이전 작업의 실패로 상태를 덮어쓰지 않는다.
              if (token.equals(check.getRequestToken())) {
                check.recordFailure(Instant.now(), code);
                check.finish(token);
              }
            });
  }

  private Portfolio lockedPortfolio(Long memberId) {
    Portfolio portfolio =
        portfolios
            .findByMemberId(memberId)
            .orElseThrow(
                () ->
                    new BusinessException(
                        PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_UNAVAILABLE));
    return portfolios.findByIdForUpdate(portfolio.getId()).orElseThrow();
  }

  private PortfolioAnalysis latest(Long memberId) {
    return analyses.findFirstByPortfolioMemberIdOrderByAnalyzedAtDescIdDesc(memberId).orElse(null);
  }

  private List<PortfolioAssetRequest> currentAssets(Portfolio portfolio) {
    var saved = holdings.findAllByPortfolioIdOrderById(portfolio.getId());
    if (saved.isEmpty()) {
      return List.of();
    }
    var calculated =
        calculation.calculate(
            new PortfolioCalculateRequest(
                saved.stream()
                    .map(
                        holding ->
                            new PortfolioHoldingRequest(
                                holding.getAsset().getId(),
                                holding.getQuantity(),
                                holding.getAveragePurchasePrice()))
                    .toList()));
    // 가격 없는 자산을 빼고 나머지를 전체 포트폴리오처럼 분석하지 않는다.
    if (calculated.unpricedHoldingCount() > 0) {
      return List.of();
    }
    var assets =
        new ArrayList<>(
            calculated.holdings().stream()
                .filter(holding -> holding.weight() != null && holding.weight().signum() > 0)
                .map(holding -> new PortfolioAssetRequest(holding.name(), holding.weight()))
                .toList());
    if (assets.isEmpty()) {
      return List.of();
    }
    int largest = 0;
    BigDecimal total = BigDecimal.ZERO;
    for (int index = 0; index < assets.size(); index++) {
      total = total.add(assets.get(index).weight());
      if (assets.get(index).weight().compareTo(assets.get(largest).weight()) > 0) {
        largest = index;
      }
    }
    PortfolioAssetRequest asset = assets.get(largest);
    assets.set(
        largest,
        new PortfolioAssetRequest(
            asset.assetName(), asset.weight().add(BigDecimal.valueOf(100).subtract(total))));
    return List.copyOf(assets);
  }

  private boolean sameAssets(List<PortfolioAssetRequest> left, List<PortfolioAssetRequest> right) {
    return PortfolioAnalysisSnapshot.sameAssets(toAssets(left), toAssets(right));
  }

  private boolean matches(PortfolioAnalysis analysis, List<PortfolioAssetRequest> assets) {
    return analysis.getSnapshot().hasSameAssets(toAssets(assets));
  }

  private List<PortfolioAnalysisSnapshot.Asset> toAssets(List<PortfolioAssetRequest> assets) {
    return assets.stream()
        .map(asset -> new PortfolioAnalysisSnapshot.Asset(asset.assetName(), asset.weight()))
        .toList();
  }

  private SavedPortfolioAnalysisResponse response(
      Long memberId,
      PortfolioAnalysis analysis,
      PortfolioAnalysisCheck check,
      List<PortfolioAssetRequest> assets,
      Instant now) {
    boolean same = analysis != null && matches(analysis, assets);
    Instant succeededAt = check == null ? null : check.getLastSucceededAt();
    Instant failedAt = check == null ? null : check.getLastFailedAt();
    Status status =
        analysis == null ? Status.EMPTY : same ? Status.READY : Status.PORTFOLIO_CHANGED;
    if (analysis != null
        && same
        && (check == null
            || check.requiresRecheck(now)
            || analysis.getSnapshot().impacts().stream()
                .noneMatch(impact -> impact.direction() != null))) {
      status = Status.RECHECK_REQUIRED;
    }
    if (failedAt != null && (succeededAt == null || failedAt.isAfter(succeededAt))) {
      status = Status.FAILED;
    }
    if (check != null && check.isProcessing(now)) {
      status = Status.PROCESSING;
    }
    return new SavedPortfolioAnalysisResponse(
        status,
        check == null || check.getLastOutcome() == null ? null : check.getLastOutcome().name(),
        analysis == null ? null : analysis.getId(),
        same,
        succeededAt,
        failedAt,
        check == null ? null : check.getFailureCode(),
        analysis == null ? null : analysis.getChangeReason(),
        analysis == null
            ? null
            : PortfolioAnalysisResponse.from(
                analysis,
                evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                    analysis.getId(), memberId)));
  }

  public record PreparedAnalysis(
      SavedPortfolioAnalysisResponse saved,
      UUID token,
      List<PortfolioAssetRequest> assets,
      PortfolioAnalysisResponse previous) {}
}
