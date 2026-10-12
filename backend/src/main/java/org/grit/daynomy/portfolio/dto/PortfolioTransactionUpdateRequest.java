package org.grit.daynomy.portfolio.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record PortfolioTransactionUpdateRequest(
    @NotNull @PastOrPresent LocalDate tradeDate,
    @NotNull @Positive Long quantity,
    @NotNull @DecimalMin("0.01") @Digits(integer = 15, fraction = 2) BigDecimal unitPrice,
    @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal fee,
    @NotNull @DecimalMin("0.00") @Digits(integer = 15, fraction = 2) BigDecimal tax) {}
