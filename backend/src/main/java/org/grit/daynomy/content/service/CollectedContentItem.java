package org.grit.daynomy.content.service;

import java.time.LocalDate;

record CollectedContentItem(
    String title, String description, String url, String imageUrl, LocalDate publishedDate) {}
