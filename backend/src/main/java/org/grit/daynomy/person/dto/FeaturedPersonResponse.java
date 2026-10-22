package org.grit.daynomy.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.grit.daynomy.person.domain.FeaturedPersonPost;

public record FeaturedPersonResponse(
    @Schema(description = "주요 인물 ID", example = "1") Long id,
    @Schema(description = "인물 이름", example = "일론 머스크") String name,
    @Schema(description = "X 프로필 URL", example = "https://x.com/elonmusk") String xProfileUrl,
    @Schema(description = "Threads 프로필 URL", example = "https://www.threads.net/@example")
        String threadsProfileUrl,
    @Schema(description = "YouTube 채널 URL", example = "https://www.youtube.com/@example")
        String youtubeProfileUrl,
    @Schema(description = "프로필 이미지 URL", example = "https://example.com/people/1.png")
        String profileImageUrl,
    @Schema(description = "등록 시각", example = "2026-10-23T09:00:00Z") Instant createdAt,
    @Schema(description = "공개 게시물 목록") List<FeaturedPersonPostResponse> posts) {

  public static FeaturedPersonResponse from(FeaturedPerson person, List<FeaturedPersonPost> posts) {
    return new FeaturedPersonResponse(
        person.getId(),
        person.getName(),
        person.getXProfileUrl(),
        person.getThreadsProfileUrl(),
        person.getYoutubeProfileUrl(),
        person.getProfileImageUrl(),
        person.getCreatedAt(),
        posts.stream().map(FeaturedPersonPostResponse::from).toList());
  }
}
