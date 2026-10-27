package org.grit.daynomy.emailsubscription.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysis;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisContent;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisKey;
import org.grit.daynomy.emailsubscription.domain.CommonStockAnalysisVerification;
import org.grit.daynomy.emailsubscription.dto.AdminEmailAnalysisDetailResponse;
import org.grit.daynomy.emailsubscription.dto.AdminEmailAnalysisItemResponse;
import org.grit.daynomy.emailsubscription.dto.AdminEmailAnalysisPageResponse;
import org.grit.daynomy.emailsubscription.dto.AnalysisInvalidationResponse;
import org.grit.daynomy.emailsubscription.exception.EmailAnalysisErrorCode;
import org.grit.daynomy.emailsubscription.repository.CommonStockAnalysisRepository;
import org.grit.daynomy.emailsubscription.repository.EmailDeliveryRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommonStockAnalysisService {

  private final EmailDeliveryRepository deliveries;
  private final AssetRepository assets;
  private final CommonStockAnalysisRepository analyses;
  private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

  public AdminEmailAnalysisPageResponse listForAdmin(
      CommonStockAnalysis.Status status, Long assetId, Pageable pageable) {
    return AdminEmailAnalysisPageResponse.from(
        analyses.findForAdmin(status, assetId, pageable).map(AdminEmailAnalysisItemResponse::from));
  }

  public AdminEmailAnalysisDetailResponse detailForAdmin(Long id) {
    return AdminEmailAnalysisDetailResponse.from(
        analyses
            .findForAdminById(id)
            .orElseThrow(
                () -> new BusinessException(EmailAnalysisErrorCode.EMAIL_ANALYSIS_NOT_FOUND)));
  }

  @Transactional
  public Long storePending(CommonStockAnalysisKey key, CommonStockAnalysisContent content) {
    Asset asset =
        assets
            .findById(key.assetId())
            .orElseThrow(() -> new IllegalArgumentException("분석 대상 자산이 존재하지 않습니다."));
    String serialized = serialize(content);
    CommonStockAnalysis pending =
        new CommonStockAnalysis(asset, key, content, hash(serialized), now());
    analyses.insertPending(
        asset.getId(),
        pending.getAssetCode(),
        pending.getMarket().name(),
        pending.getCategory().name(),
        key.model(),
        key.analysisVersion(),
        key.evidenceVersion(),
        key.asOf(),
        serialized,
        pending.getContentHash(),
        pending.getAnalyzedAt());
    return analyses.findByKey(key).orElseThrow().getId();
  }

  public Optional<CommonStockAnalysis> findReusable(CommonStockAnalysisKey key) {
    return analyses.findReusable(key);
  }

  public Optional<CommonStockAnalysis> findStored(CommonStockAnalysisKey key) {
    return analyses.findByKey(key);
  }

  @Transactional
  public void recordVerification(Long analysisId, CommonStockAnalysisVerification verification) {
    if (verification.checkedAt().isAfter(now())) {
      throw new IllegalArgumentException("미래 시점의 검증 결과는 사용할 수 없습니다.");
    }
    CommonStockAnalysis analysis =
        analyses
            .findByIdForUpdate(analysisId)
            .orElseThrow(() -> new IllegalArgumentException("검증할 공통 분석이 존재하지 않습니다."));
    // 동시 검증은 먼저 확정된 결과를 유지한다. 다른 내용의 검증은 도메인에서 거부한다.
    if (analysis.getStatus() != CommonStockAnalysis.Status.PENDING
        && analysis.getContentHash().equals(verification.analysisContentHash())) return;
    analysis.recordVerification(verification);
  }

  @Transactional
  public AnalysisInvalidationResponse invalidate(Long analysisId, String reason) {
    var analysis =
        analyses
            .findByIdForUpdate(analysisId)
            .orElseThrow(
                () -> new BusinessException(EmailAnalysisErrorCode.EMAIL_ANALYSIS_NOT_FOUND));
    analysis.invalidate(reason, now());
    int cancelled = deliveries.cancelQueuedForAnalysis(analysisId);
    return new AnalysisInvalidationResponse(analysisId, analysis.getInvalidatedAt(), cancelled);
  }

  public List<UUID> correctionReviewIds(Long analysisId, Pageable pageable) {
    if (!analyses.existsById(analysisId))
      throw new BusinessException(EmailAnalysisErrorCode.EMAIL_ANALYSIS_NOT_FOUND);
    return deliveries.findCorrectionReviewIds(analysisId, pageable);
  }

  private String serialize(CommonStockAnalysisContent content) {
    try {
      return mapper.writeValueAsString(content);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("공통 분석을 저장 형식으로 변환하지 못했습니다.", exception);
    }
  }

  private Instant now() {
    return Instant.now().truncatedTo(ChronoUnit.MICROS);
  }

  private String hash(String content) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256")
                  .digest(content.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("분석 내용의 해시를 계산하지 못했습니다.", exception);
    }
  }
}
