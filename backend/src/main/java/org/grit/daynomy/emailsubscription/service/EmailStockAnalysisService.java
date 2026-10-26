package org.grit.daynomy.emailsubscription.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis.Status;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisContent;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisKey;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisVerification;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisAiClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisDocumentClient;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisResult;
import org.grit.daynomy.portfolio.ai.PortfolioAnalysisTarget;
import org.grit.daynomy.portfolio.domain.PortfolioAnalysisSourceDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class EmailStockAnalysisService {
  private static final String ANALYSIS_VERSION = "email-stock-v1";
  private static final String EVIDENCE_VERSION = "original-source-v1";
  private final ImportantStockSelectionService selection;
  private final AssetRepository assets;
  private final CommonStockAnalysisService common;
  private final PortfolioAnalysisAiClient ai;
  private final PortfolioAnalysisDocumentClient documents;
  private final String model;

  public EmailStockAnalysisService(
      ImportantStockSelectionService selection,
      AssetRepository assets,
      CommonStockAnalysisService common,
      PortfolioAnalysisAiClient ai,
      PortfolioAnalysisDocumentClient documents,
      @Value("${portfolio.analysis.model:${ai.openai.model:gpt-5-mini}}") String model) {
    this.selection = selection;
    this.assets = assets;
    this.common = common;
    this.ai = ai;
    this.documents = documents;
    this.model = model;
  }

  // 외부 호출 중에는 DB 트랜잭션을 유지하지 않는다. 예약 실행은 비용 제어 구현 후 연결한다.
  public List<CommonStockAnalysis> analyze(Long memberId, Instant asOf) {
    if (asOf.isAfter(Instant.now())) throw new IllegalArgumentException("미래 기준의 분석은 실행할 수 없습니다.");
    var selected = selection.select(memberId, asOf.atZone(ZoneId.of("Asia/Seoul")).toLocalDate());
    if (selected.isEmpty() || selected.get().stocks().isEmpty()) return List.of();
    var ids =
        selected.get().stocks().stream().map(stock -> stock.assetId()).distinct().limit(3).toList();
    return analyzeAssets(ids, asOf);
  }

  // 예약 작업은 중복 제거와 실행권 확보를 마친 단일 종목만 요청한다.
  public java.util.Optional<CommonStockAnalysis> analyzeStock(Long assetId, Instant asOf) {
    if (asOf.isAfter(Instant.now())) throw new IllegalArgumentException("미래 기준의 분석은 실행할 수 없습니다.");
    return analyzeAssets(List.of(assetId), asOf).stream().findFirst();
  }

  private List<CommonStockAnalysis> analyzeAssets(List<Long> ids, Instant asOf) {
    Map<Long, Asset> byId = new LinkedHashMap<>();
    assets.findAllById(ids).forEach(asset -> byId.put(asset.getId(), asset));
    var result = new ArrayList<CommonStockAnalysis>();
    for (Long id : ids) {
      Asset asset = byId.get(id);
      if (asset == null
          || asset.getMarket() == null
          || asset.getAssetCode() == null
          || asset.getAssetCode().isBlank()
          || (asset.getCategory() != AssetCategory.STOCK
              && asset.getCategory() != AssetCategory.ETF)) continue;
      var key = new CommonStockAnalysisKey(id, model, ANALYSIS_VERSION, EVIDENCE_VERSION, asOf);
      var reusable = common.findReusable(key);
      if (reusable.isPresent()) {
        result.add(reusable.get());
        continue;
      }
      var saved = common.findStored(key);
      if (saved.isEmpty()) {
        // 공통 AI 입력에는 식별 정보만 넣는다. 수량·비중·손익은 전달하지 않는다.
        String label =
            asset.getName()
                + " ["
                + asset.getAssetCode()
                + ", "
                + asset.getMarket()
                + ", "
                + asset.getCategory()
                + "]";
        PortfolioAnalysisResult generated;
        try {
          generated = ai.analyze(List.of(new PortfolioAnalysisTarget(label)));
        } catch (BusinessException exception) {
          log.atWarn()
              .addKeyValue("event", "email.stock.analysis.failed")
              .addKeyValue("assetId", id)
              .log("이메일 종목 분석 실패");
          continue;
        }
        var matching =
            generated.impacts().stream()
                .filter(impact -> label.equals(impact.assetName()))
                .toList();
        if (matching.size() != 1) continue;
        var impact = matching.getFirst();
        common.storePending(
            key,
            new CommonStockAnalysisContent(
                impact.direction(),
                impact.impactLevel(),
                impact.issueSummary(),
                impact.expectedReaction(),
                impact.outlook(),
                impact.reason(),
                impact.sources()));
        saved = common.findStored(key);
      }
      if (saved.isEmpty() || saved.get().getStatus() != Status.PENDING) continue;
      var analysis = saved.get();
      if (!analysis.getAssetCode().equals(asset.getAssetCode())
          || analysis.getMarket() != asset.getMarket()
          || analysis.getCategory() != asset.getCategory()) continue;
      var urls =
          analysis.getContent().sources().stream().map(source -> source.url()).distinct().toList();
      Map<String, PortfolioAnalysisSourceDocument> fetched = new LinkedHashMap<>();
      documents
          .fetchAll(urls)
          .forEach((url, document) -> document.ifPresent(value -> fetched.put(url, value)));
      // 수집 실패는 대기 상태를 보존하므로 다음 검증에서 AI를 재호출하지 않는다.
      if (!fetched.keySet().containsAll(urls)) continue;
      common.recordVerification(
          analysis.getId(),
          CommonStockAnalysisVerification.fromOriginals(asset, analysis, fetched, Instant.now()));
      common.findReusable(key).ifPresent(result::add);
    }
    return List.copyOf(result);
  }
}
