package org.grit.daynomy.search.dto;

import java.util.List;

public record ContentSearchResponse(
    List<ContentSearchItemResponse> items,
    int page,
    int size,
    long totalElements,
    int totalPages) {}
