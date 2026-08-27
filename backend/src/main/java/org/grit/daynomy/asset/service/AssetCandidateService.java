package org.grit.daynomy.asset.service;

import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.AssetRankingHistory;
import org.grit.daynomy.asset.dto.AssetCandidateResponse;
import org.grit.daynomy.asset.dto.AssetCandidatesResponse;
import org.grit.daynomy.asset.repository.AssetRankingHistoryRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AssetCandidateService {

  private final AssetRankingHistoryRepository assetRankingHistoryRepository;

  @Transactional(readOnly = true)
  public AssetCandidatesResponse getKosdaqTopRankings(String keyword, int page, int size) {
    return assetRankingHistoryRepository
        .findFirstByOrderByRankedDateDesc()
        .map(AssetRankingHistory::getRankedDate)
        .map(rankedDate -> getRankingsByDate(rankedDate, keyword, page, size))
        .orElseGet(() -> AssetCandidatesResponse.empty(page, size));
  }

  private AssetCandidatesResponse getRankingsByDate(
      LocalDate rankedDate, String keyword, int page, int size) {
    PageRequest pageable = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.ASC, "ranking"));
    var rankings =
        keyword == null
            ? assetRankingHistoryRepository.findAllByRankedDate(rankedDate, pageable)
            : assetRankingHistoryRepository.searchByRankedDateAndKeyword(
                rankedDate, escapeLikeKeyword(keyword.strip()), pageable);

    return AssetCandidatesResponse.from(
        rankedDate.toString(), rankings.map(AssetCandidateResponse::from));
  }

  private String escapeLikeKeyword(String keyword) {
    return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }
}
