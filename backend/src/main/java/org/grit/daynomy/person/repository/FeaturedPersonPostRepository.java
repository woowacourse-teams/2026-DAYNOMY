package org.grit.daynomy.person.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.person.domain.FeaturedPersonPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeaturedPersonPostRepository extends JpaRepository<FeaturedPersonPost, Long> {

  List<FeaturedPersonPost> findAllByOrderByCreatedAtDescIdDesc();

  boolean existsByPersonIdAndUrl(Long personId, String url);

  Optional<FeaturedPersonPost> findByIdAndPersonId(Long id, Long personId);
}
