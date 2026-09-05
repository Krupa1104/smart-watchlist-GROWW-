package com.groww.smart_watchlist.dto;

import com.groww.smart_watchlist.entity.InstrumentType;

import java.util.List;

// Backs the frontend's instrument detail panel (click a row in the
// watchlist table). Deliberately composed entirely from shapes the rest of
// the API already returns elsewhere (MarketDataResponse, DetectedChangeResponse,
// SnapshotDiffResponse) plus two new, narrowly-scoped additions
// (RelatedEventResponse, suggestedActions) — nothing here invents a new
// shape for data the app already models elsewhere.
public record InstrumentDetailResponse(
        String symbol,
        InstrumentType instrumentType,
        MarketDataResponse marketData,
        List<PricePointResponse> recentHistory,
        DetectedChangeResponse detectedChange,
        SnapshotDiffResponse sinceLastCheck, // firstView=true if this item has never been checked before
        RelatedEventResponse relatedEvent,   // null when no planted event falls within the correlation window
        List<String> suggestedActions        // empty when the detected change isn't meaningful
) {
}
