package org.grit.daynomy.external.wikimedia;

public record WikimediaImageCandidate(
    String title,
    String thumbnailUrl,
    String sourceUrl,
    String author,
    String license,
    String licenseUrl,
    int width,
    int height) {}
