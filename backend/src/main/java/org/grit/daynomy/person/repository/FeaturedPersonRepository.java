package org.grit.daynomy.person.repository;

import java.util.List;
import org.grit.daynomy.person.domain.FeaturedPerson;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeaturedPersonRepository extends JpaRepository<FeaturedPerson, Long> {

  List<FeaturedPerson> findAllByOrderByCreatedAtAscIdAsc();

  boolean existsByName(String name);

  boolean existsByNameAndIdNot(String name, Long id);
}
