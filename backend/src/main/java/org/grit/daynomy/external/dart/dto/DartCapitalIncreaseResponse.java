package org.grit.daynomy.external.dart.dto;

import java.util.List;

public record DartCapitalIncreaseResponse(
    String status, String message, List<DartCapitalIncreaseItem> list) {

  public DartCapitalIncreaseResponse {
    if ("013".equals(status) && list == null) {
      list = List.of();
    }
  }
}
