package com.groww.smart_watchlist.dto;

import com.groww.smart_watchlist.entity.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;

// latestValue is close price for a STOCK, NAV for a FUND — deliberately one
// field, not separate price/nav fields, so the digest layer (Phase 3/4) can
// treat both instrument types uniformly ("value moved from X to Y").
// asOfDate is exposed per-item (rather than assumed) so the response itself
// carries the staleness signal the brief calls out: if an item's asOfDate is
// older than the watchlist's overall dataAsOf, the frontend can flag it
// instead of silently showing a stale number as if it were current.
public record MarketDataResponse(
        String symbol,
        InstrumentType instrumentType,
        String displayName,
        String groupLabel, // sector for stocks, category for funds
        BigDecimal latestValue,
        LocalDate asOfDate,
        boolean dataAvailable // false when no price/NAV row exists at all yet
) {
}
