package org.grit.daynomy.external.dart.dto;

import java.util.List;

public record DartMergerDecisionResponse(
    String status, String message, List<DartMergerDecisionItem> list) {

  public DartMergerDecisionResponse {
    if ("013".equals(status) && list == null) {
      list = List.of();
    }
  }
}
