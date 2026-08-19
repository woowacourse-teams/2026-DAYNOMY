package org.grit.daynomy.external.dart.dto;

import java.util.List;

public record DartConvertibleBondResponse(
    String status, String message, List<DartConvertibleBondItem> list) {

  public DartConvertibleBondResponse {
    if ("013".equals(status) && list == null) {
      list = List.of();
    }
  }
}
