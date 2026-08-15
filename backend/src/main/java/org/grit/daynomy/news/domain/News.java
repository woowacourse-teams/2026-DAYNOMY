package org.grit.daynomy.news.domain;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
public class News {

    private Long id;

    private String title;
    private String content;
    private String description;

    private String imageUrl;

    private Set<Category> categories = new HashSet<>();

    // TODO(choiyoung69): Keyword 관계 설계 확정 후 추가

    private LocalDateTime publishedAt;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
