package org.grit.daynomy.external.dart.dto;

import java.util.List;

public record DartMergerDecisionResponse(
    String status, String message, List<DartMergerDecisionItem> list) {}
