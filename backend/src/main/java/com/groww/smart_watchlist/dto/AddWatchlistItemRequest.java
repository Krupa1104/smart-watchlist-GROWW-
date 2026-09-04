package com.groww.smart_watchlist.dto;

import com.groww.smart_watchlist.entity.InstrumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AddWatchlistItemRequest(
        @NotBlank(message = "is required")
        String symbol,

        @NotNull(message = "is required (STOCK or FUND)")
        InstrumentType instrumentType
) {
}
