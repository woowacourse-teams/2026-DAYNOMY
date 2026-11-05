package org.grit.daynomy.portfolio.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record PortfolioTransactionImportRequest(
    @NotEmpty @Size(max = 200) List<@NotNull @Valid PortfolioTransactionRequest> transactions) {}
