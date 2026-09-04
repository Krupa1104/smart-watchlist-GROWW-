package com.groww.smart_watchlist.dto;

import java.time.OffsetDateTime;

// Used for the "list my watchlists" endpoint. Deliberately excludes market
// data — fetching current price/NAV for every item of every watchlist just
// to render a picker list would be wasted work; full detail is one click
// away via GET /api/watchlists/{id}.
public record WatchlistSummaryResponse(
        Integer id,
        String name,
        OffsetDateTime createdAt,
        int itemCount
) {
}
