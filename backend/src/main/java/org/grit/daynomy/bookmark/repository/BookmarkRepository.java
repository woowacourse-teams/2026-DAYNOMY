package org.grit.daynomy.bookmark.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.bookmark.domain.Bookmark;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BookmarkRepository extends JpaRepository<Bookmark, Long> {

  boolean existsByMemberIdAndAssetId(Long memberId, Long assetId);

  Optional<Bookmark> findByMemberIdAndAssetId(Long memberId, Long assetId);

  List<Bookmark> findAllByMemberIdOrderByIdAsc(Long memberId);
}
