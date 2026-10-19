package org.grit.daynomy.exchangerate.repository;

import java.time.LocalDate;
import org.grit.daynomy.exchangerate.domain.UsdKrwRate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UsdKrwRateRepository extends JpaRepository<UsdKrwRate, LocalDate> {}
