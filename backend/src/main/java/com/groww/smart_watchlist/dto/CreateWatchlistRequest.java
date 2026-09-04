package com.groww.smart_watchlist.dto;

import jakarta.validation.constraints.Size;

public record CreateWatchlistRequest(
        @Size(max = 100, message = "must be at most 100 characters")
        String name // optional — Watchlist.name defaults to "My Watchlist" if blank
) {
}
