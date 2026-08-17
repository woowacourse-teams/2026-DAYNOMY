package org.grit.daynomy.external.dart.dto;

import java.util.List;

public record DartConvertibleBondResponse(
    String status, String message, List<DartConvertibleBondItem> list) {}
