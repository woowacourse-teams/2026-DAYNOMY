package org.grit.daynomy.external.dart.dto;

import java.util.List;

public record DartDisclosureResponse(String status, String message, List<DartDisclosureItem> list) {

  public DartDisclosureResponse {
    if ("013".equals(status) && list == null) {
      list = List.of();
    }
  }
}
