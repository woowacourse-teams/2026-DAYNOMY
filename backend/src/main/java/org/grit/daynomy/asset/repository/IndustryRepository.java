package org.grit.daynomy.asset.repository;

import java.util.List;
import java.util.Optional;
import org.grit.daynomy.asset.domain.Industry;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IndustryRepository extends JpaRepository<Industry, Long> {

  List<Industry> findAllByActiveTrueOrderByDisplayOrderAscIdAsc();

  List<Industry> findAllByOrderByDisplayOrderAscIdAsc();

  Optional<Industry> findByCodeAndActiveTrue(String code);

  boolean existsByCode(String code);
}
