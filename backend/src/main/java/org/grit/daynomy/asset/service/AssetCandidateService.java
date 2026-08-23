package org.grit.daynomy.asset.service;

import java.util.List;
import org.grit.daynomy.asset.dto.AssetCandidatesResponse;
import org.springframework.stereotype.Service;

@Service
public class AssetCandidateService {

  public AssetCandidatesResponse getKosdaqTopRankings() {
    return new AssetCandidatesResponse(null, List.of());
  }
}
