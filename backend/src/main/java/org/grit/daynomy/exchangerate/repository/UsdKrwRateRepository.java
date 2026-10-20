package org.grit.daynomy.exchangerate.repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.grit.daynomy.exchangerate.domain.UsdKrwRate;
import org.springframework.data.jpa.repository.*;
import org.springframework.transaction.annotation.Transactional;

public interface UsdKrwRateRepository extends JpaRepository<UsdKrwRate, LocalDate> {
  @Modifying
  @Transactional
  @Query(
      value =
          """
      INSERT INTO usd_krw_rates (requested_date, base_date, rate, fetched_at)
      VALUES (:requestedDate, :baseDate, :rate, :fetchedAt)
      ON CONFLICT (requested_date) DO UPDATE
      SET base_date = EXCLUDED.base_date, rate = EXCLUDED.rate, fetched_at = EXCLUDED.fetched_at
      WHERE usd_krw_rates.fetched_at <= EXCLUDED.fetched_at
      """,
      nativeQuery = true)
  void upsert(LocalDate requestedDate, LocalDate baseDate, BigDecimal rate, Instant fetchedAt);
}
