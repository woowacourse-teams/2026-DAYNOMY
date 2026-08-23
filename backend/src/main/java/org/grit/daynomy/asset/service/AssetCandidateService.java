package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.grit.daynomy.asset.dto.AssetCandidateResponse;
import org.grit.daynomy.asset.dto.AssetCandidatesResponse;
import org.grit.daynomy.asset.repository.AssetRankingHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AssetCandidateService {

  private final AssetRankingHistoryRepository assetRankingHistoryRepository;

  @Transactional(readOnly = true)
  public AssetCandidatesResponse getKosdaqTopRankings() {
    return assetRankingHistoryRepository
        .findFirstByOrderByRankedDateDesc()
        .map(AssetRankingHistory::getRankedDate)
        .map(this::getRankingsByDate)
        .orElseGet(() -> new AssetCandidatesResponse(null, List.of()));
  }

  private AssetCandidatesResponse getRankingsByDate(LocalDate rankedDate) {
    List<AssetCandidateResponse> rankings =
        assetRankingHistoryRepository.findAllByRankedDateOrderByRankingAsc(rankedDate).stream()
            .map(AssetCandidateResponse::from)
            .toList();
    return new AssetCandidatesResponse(rankedDate.toString(), rankings);
  }
}
