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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Primary
@Service
public class SavedPortfolioAnalysisTransactionService {

  private static final String ANALYSIS_VERSION = "portfolio-v9-grounded-source";
  private static final Duration REQUEST_TIMEOUT = Duration.ofMinutes(5);
  private static final int RETAINED_ANALYSES = 10;

  private final PortfolioRepository portfolios;
  private final PortfolioHoldingRepository holdings;
  private final PortfolioAnalysisRepository analyses;
  private final PortfolioAnalysisEvidenceRepository evidence;
  private final PortfolioAnalysisCheckRepository checks;
  private final PortfolioCalculationService calculation;
  private final PlatformTransactionManager transactionManager;
  private final String model;
  private final String analysisVersion;

  @Autowired
  public SavedPortfolioAnalysisTransactionService(
      PortfolioRepository portfolios,
      PortfolioHoldingRepository holdings,
      PortfolioAnalysisRepository analyses,
      PortfolioAnalysisEvidenceRepository evidence,
      PortfolioAnalysisCheckRepository checks,
      PortfolioCalculationService calculation,
      PlatformTransactionManager transactionManager,
      @Value("${portfolio.analysis.model:${ai.openai.model:gpt-5-mini}}") String model) {
    this(
        portfolios,
        holdings,
        analyses,
        evidence,
        checks,
        calculation,
        transactionManager,
        model,
        ANALYSIS_VERSION);
  }

  public SavedPortfolioAnalysisTransactionService(
      PortfolioRepository portfolios,
      PortfolioHoldingRepository holdings,
      PortfolioAnalysisRepository analyses,
      PortfolioAnalysisEvidenceRepository evidence,
      PortfolioAnalysisCheckRepository checks,
      PortfolioCalculationService calculation,
      PlatformTransactionManager transactionManager,
      String model,
      String analysisVersion) {
    this.portfolios = portfolios;
    this.holdings = holdings;
    this.analyses = analyses;
    this.evidence = evidence;
    this.checks = checks;
    this.calculation = calculation;
    this.transactionManager = transactionManager;
    this.model = model;
    this.analysisVersion = analysisVersion;
  }

  // 조회 도중 설명이 완성돼도 분석과 작업 상태를 서로 다른 시점으로 조합하지 않는다.
  public SavedPortfolioAnalysisResponse get(Long memberId) {
    var prices = currentPrices(memberId);
    var read = new TransactionTemplate(transactionManager);
    read.setReadOnly(true);
    read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    return read.execute(status -> getInTransaction(memberId, prices));
  }

  private SavedPortfolioAnalysisResponse getInTransaction(
      Long memberId, PortfolioCalculationService.PreparedPrices prices) {
    return portfolios
        .findByMemberId(memberId)
        .map(
            portfolio ->
                response(
                    memberId,
                    latest(memberId),
                    checks.findById(portfolio.getId()).orElse(null),
                    currentAssets(portfolio, prices),
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

  public PreparedAnalysis prepare(Long memberId, PortfolioAnalysisRequest request) {
    return prepare(memberId, request, false);
  }

  public PreparedAnalysis prepareRefresh(Long memberId) {
    return prepare(memberId, null, true);
  }

  public PreparedAnalysis prepareTask(Long memberId, boolean refresh) {
    return prepare(memberId, null, refresh, true);
  }

  private PreparedAnalysis prepare(
      Long memberId, PortfolioAnalysisRequest request, boolean refresh) {
    return prepare(memberId, request, refresh, false);
  }

  private PreparedAnalysis prepare(
      Long memberId, PortfolioAnalysisRequest request, boolean refresh, boolean reuseProcessing) {
    var prices = currentPrices(memberId);
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> prepareInTransaction(memberId, request, refresh, reuseProcessing, prices));
  }

  private PreparedAnalysis prepareInTransaction(
      Long memberId,
      PortfolioAnalysisRequest request,
      boolean refresh,
      boolean reuseProcessing,
      PortfolioCalculationService.PreparedPrices prices) {
    Portfolio portfolio = lockedPortfolio(memberId);
    List<PortfolioAssetRequest> assets = currentAssets(portfolio, prices);
    if (assets.isEmpty()) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_UNAVAILABLE);
    }
    // 비회원 브라우저에 남은 자산을 회원 분석으로 저장하지 않는다.
    if (request != null && !sameRequestedAssets(request.assets(), assets)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_CHANGED);
    }
    PortfolioAnalysis previous = latest(memberId);
    PortfolioAnalysisCheck check =
        checks
            .findById(portfolio.getId())
            .orElseGet(() -> checks.save(new PortfolioAnalysisCheck(portfolio)));
    Instant now = Instant.now();
    if (check.isProcessing(now) && reuseProcessing) {
      return new PreparedAnalysis(
          response(memberId, previous, check, assets, now), null, assets, null);
    }
    if (check.isProcessing(now)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_IN_PROGRESS);
    }
    if (previous != null
        && previous.getSummaryStatus() == PortfolioAnalysis.SummaryStatus.PENDING) {
      previous.failSummary();
    }
    if (!refresh
        && previous != null
        && matches(previous, assets)
        && analysisVersion.equals(previous.getAnalysisVersion())
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
                && analysisVersion.equals(previous.getAnalysisVersion())
                && model.equals(previous.getModel())
            ? PortfolioAnalysisResponse.from(
                previous,
                evidence.findAllByAnalysisIdAndAnalysisPortfolioMemberIdOrderById(
                    previous.getId(), memberId))
            : null);
  }

  public SavedPortfolioAnalysisResponse complete(
      Long memberId,
      UUID token,
      List<PortfolioAssetRequest> assets,
      PortfolioAnalysisResponse result) {
    return complete(memberId, token, assets, result, false);
  }

  public SavedPortfolioAnalysisResponse completeCore(
      Long memberId,
      UUID token,
      List<PortfolioAssetRequest> assets,
      PortfolioAnalysisResponse result) {
    return complete(memberId, token, assets, result, true);
  }

  private SavedPortfolioAnalysisResponse complete(
      Long memberId,
      UUID token,
      List<PortfolioAssetRequest> assets,
      PortfolioAnalysisResponse result,
      boolean deferSummary) {
    var prices = currentPrices(memberId);
    return new TransactionTemplate(transactionManager)
        .execute(
            status -> completeInTransaction(memberId, token, assets, result, deferSummary, prices));
  }

  private SavedPortfolioAnalysisResponse completeInTransaction(
      Long memberId,
      UUID token,
      List<PortfolioAssetRequest> assets,
      PortfolioAnalysisResponse result,
      boolean deferSummary,
      PortfolioCalculationService.PreparedPrices prices) {
    Portfolio portfolio = lockedPortfolio(memberId);
    PortfolioAnalysisCheck check = checks.findById(portfolio.getId()).orElseThrow();
    Instant now = Instant.now();
    if (!check.ownsRequest(token, now)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_REQUEST_EXPIRED);
    }
    if (!sameAssets(assets, currentAssets(portfolio, prices))) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_ASSETS_CHANGED);
    }
    PortfolioAnalysis previous = latest(memberId);
    String reason =
        previous == null ? "최초 분석" : matches(previous, assets) ? "분석 기준 변경" : "보유 종목·수량·비중 변경";
    if (previous != null
        && matches(previous, assets)
        && analysisVersion.equals(previous.getAnalysisVersion())
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
      if (!result.impacts().equals(revision.analysis().impacts())) {
        throw new IllegalStateException("최종 병합 결과와 전체 설명의 분석 대상이 다릅니다.");
      }
      result = revision.analysis().withOverallImpact(result.overallImpact());
      reason = revision.reason();
    }
    PortfolioAnalysis analysis =
        PortfolioAnalysis.create(
            portfolio,
            previous,
            analysisVersion,
            model,
            result.toSnapshot(assets),
            result.analyzedAt(),
            reason);
    if (deferSummary) {
      analysis.awaitSummary();
    }
    analyses.save(analysis);
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
    if (!deferSummary) {
      check.finish(token);
    }
    analyses.flush();
    analyses.deleteOlderAnalyses(portfolio.getId(), RETAINED_ANALYSES);
    return response(memberId, analysis, check, assets, now);
  }

  public SavedPortfolioAnalysisResponse completeSummary(
      Long memberId, UUID token, Long analysisId, String text) {
    var prices = currentPrices(memberId);
    return new TransactionTemplate(transactionManager)
        .execute(status -> completeSummaryInTransaction(memberId, token, analysisId, text, prices));
  }

  private SavedPortfolioAnalysisResponse completeSummaryInTransaction(
      Long memberId,
      UUID token,
      Long analysisId,
      String text,
      PortfolioCalculationService.PreparedPrices prices) {
    Portfolio portfolio = lockedPortfolio(memberId);
    PortfolioAnalysisCheck check = checks.findById(portfolio.getId()).orElseThrow();
    Instant now = Instant.now();
    if (!check.ownsRequest(token, now)) {
      throw new BusinessException(PortfolioErrorCode.PORTFOLIO_ANALYSIS_REQUEST_EXPIRED);
    }
    PortfolioAnalysis analysis =
        analyses.findByIdAndPortfolioMemberId(analysisId, memberId).orElseThrow();
    analysis.completeSummary(text);
    check.finish(token);
    return response(memberId, analysis, check, currentAssets(portfolio, prices), now);
  }

  @Transactional
  public void failSummary(Long memberId, UUID token, Long analysisId) {
    Portfolio portfolio = lockedPortfolio(memberId);
    PortfolioAnalysisCheck check = checks.findById(portfolio.getId()).orElseThrow();
    if (!token.equals(check.getRequestToken())) {
      return;
    }
    PortfolioAnalysis analysis =
        analyses.findByIdAndPortfolioMemberId(analysisId, memberId).orElseThrow();
    analysis.failSummary();
    check.finish(token);
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

  // 환율 수집은 DB 트랜잭션·포트폴리오 잠금을 잡기 전에 끝낸다.
  private PortfolioCalculationService.PreparedPrices currentPrices(Long memberId) {
    var request =
        portfolios
            .findByMemberId(memberId)
            .map(this::currentHoldings)
            .orElseGet(() -> new PortfolioCalculateRequest(List.of()));
    return calculation.preparePrices(request);
  }

  private PortfolioCalculateRequest currentHoldings(Portfolio portfolio) {
    return new PortfolioCalculateRequest(
        holdings.findAllByPortfolioIdOrderById(portfolio.getId()).stream()
            .map(
                holding ->
                    new PortfolioHoldingRequest(
                        holding.getAsset().getId(),
                        holding.getQuantity(),
                        holding.getAveragePurchasePrice()))
            .toList());
  }

  private List<PortfolioAssetRequest> currentAssets(
      Portfolio portfolio, PortfolioCalculationService.PreparedPrices prices) {
    var request = currentHoldings(portfolio);
    if (request.holdings().isEmpty()) return List.of();
    var calculated = calculation.calculate(request, prices);
    // 가격 없는 자산을 빼고 나머지를 전체 포트폴리오처럼 분석하지 않는다.
    if (calculated.unpricedHoldingCount() > 0) {
      return List.of();
    }
    var assets =
        new ArrayList<>(
            calculated.holdings().stream()
                .filter(holding -> holding.weight() != null && holding.weight().signum() > 0)
                .map(
                    holding ->
                        new PortfolioAssetRequest(
                            holding.name(), holding.weight(), holding.quantity()))
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
            asset.assetName(),
            asset.weight().add(BigDecimal.valueOf(100).subtract(total)),
            asset.quantity()));
    return List.copyOf(assets);
  }

  // 요청의 수량을 신뢰하지 않고 서버 보유 내역으로 분석 스냅샷을 만든다.
  private boolean sameRequestedAssets(
      List<PortfolioAssetRequest> left, List<PortfolioAssetRequest> right) {
    return PortfolioAnalysisSnapshot.sameAssets(
        left.stream()
            .map(asset -> new PortfolioAnalysisSnapshot.Asset(asset.assetName(), asset.weight()))
            .toList(),
        right.stream()
            .map(asset -> new PortfolioAnalysisSnapshot.Asset(asset.assetName(), asset.weight()))
            .toList());
  }

  private boolean sameAssets(List<PortfolioAssetRequest> left, List<PortfolioAssetRequest> right) {
    return PortfolioAnalysisSnapshot.sameAssets(toAssets(left), toAssets(right));
  }

  private boolean matches(PortfolioAnalysis analysis, List<PortfolioAssetRequest> assets) {
    return analysis.getSnapshot().hasSameAssets(toAssets(assets));
  }

  private List<PortfolioAnalysisSnapshot.Asset> toAssets(List<PortfolioAssetRequest> assets) {
    return assets.stream()
        .map(
            asset ->
                new PortfolioAnalysisSnapshot.Asset(
                    asset.assetName(), asset.weight(), asset.quantity()))
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
        && (!analysisVersion.equals(analysis.getAnalysisVersion())
            || check == null
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
                    analysis.getId(), memberId)),
        summaryStatus(analysis, check, now));
  }

  private String summaryStatus(
      PortfolioAnalysis analysis, PortfolioAnalysisCheck check, Instant now) {
    if (analysis == null) {
      return null;
    }
    if (analysis.getSummaryStatus() == PortfolioAnalysis.SummaryStatus.PENDING
        && (check == null || !check.isProcessing(now))) {
      return PortfolioAnalysis.SummaryStatus.FAILED.name();
    }
    return analysis.getSummaryStatus().name();
  }

  public record PreparedAnalysis(
      SavedPortfolioAnalysisResponse saved,
      UUID token,
      List<PortfolioAssetRequest> assets,
      PortfolioAnalysisResponse previous) {}
}
