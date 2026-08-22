package org.grit.daynomy.bookmark.repository;

import org.grit.daynomy.bookmark.domain.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

  boolean existsByMemberIdAndTargetId(Long memberId, Long targetId);
}
