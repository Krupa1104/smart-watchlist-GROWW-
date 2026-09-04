package com.groww.smart_watchlist.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record WatchlistResponse(
        Integer id,
        Integer userId,
        String name,
        OffsetDateTime createdAt,
        LocalDate dataAsOf, // max asOfDate across items — items older than this are stale
        List<WatchlistItemResponse> items
) {
}
