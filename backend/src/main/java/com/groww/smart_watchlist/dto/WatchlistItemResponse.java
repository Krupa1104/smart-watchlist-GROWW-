package com.groww.smart_watchlist.dto;

import com.groww.smart_watchlist.entity.InstrumentType;

import java.time.OffsetDateTime;

public record WatchlistItemResponse(
        Integer itemId,
        String symbol,
        InstrumentType instrumentType,
        OffsetDateTime addedAt,
        MarketDataResponse marketData
) {
}
