package org.grit.daynomy.emailsubscription.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.domain.EmailStockDigest;
import org.grit.daynomy.emailsubscription.repository.CommonStockAnalysisRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmailStockDigestService {
  private final ImportantStockSelectionService selection;
  private final CommonStockAnalysisRepository analyses;

  /** AI 호출 없이 현재 회원의 선별 결과와 검증된 공통 분석을 연결한다. */
  public Optional<EmailStockDigest> compose(Long memberId, Instant asOf, List<Long> analysisIds) {
    if (asOf.isAfter(Instant.now())) throw new IllegalArgumentException("미래 기준의 메일은 구성할 수 없습니다.");
    var selected = selection.select(memberId, asOf.atZone(ZoneId.of("Asia/Seoul")).toLocalDate());
    if (selected.isEmpty() || selected.get().stocks().isEmpty() || analysisIds.isEmpty())
      return Optional.empty();
    var byAsset =
        analyses.findAllById(analysisIds.stream().distinct().limit(3).toList()).stream()
            .filter(analysis -> usable(analysis, asOf))
            .collect(
                Collectors.toMap(
                    analysis -> analysis.getAsset().getId(),
                    Function.identity(),
                    (first, ignored) -> first));
    var stocks =
        selected.get().stocks().stream()
            .limit(3)
            .filter(stock -> byAsset.containsKey(stock.assetId()))
            .map(
                stock -> {
                  var analysis = byAsset.get(stock.assetId());
                  return new EmailStockDigest.Stock(
                      analysis.getId(),
                      stock.assetId(),
                      analysis.getAsset().getName(),
                      stock.dailyReturnRate(),
                      stock.weight(),
                      stock.contribution(),
                      analysis.getContent());
                })
            .toList();
    if (stocks.isEmpty()) return Optional.empty();
    return Optional.of(
        new EmailStockDigest(selected.get().baseDate(), selected.get().previousBaseDate(), stocks));
  }

  private boolean usable(CommonStockAnalysis analysis, Instant asOf) {
    var asset = analysis.getAsset();
    return analysis.getStatus() == CommonStockAnalysis.Status.VERIFIED
        && analysis.getAsOf().equals(asOf.truncatedTo(ChronoUnit.MICROS))
        && Objects.equals(analysis.getAssetCode(), asset.getAssetCode())
        && analysis.getMarket() == asset.getMarket()
        && analysis.getCategory() == asset.getCategory();
  }
}
