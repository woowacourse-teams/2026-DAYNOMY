package org.grit.daynomy.content.dto;

import java.util.List;
import org.springframework.data.domain.Slice;

public record DiscoverableAssetsResponse(List<Long> assetIds, boolean hasNext) {
  public static DiscoverableAssetsResponse from(Slice<Long> assets) {
    return new DiscoverableAssetsResponse(assets.getContent(), assets.hasNext());
  }
}
