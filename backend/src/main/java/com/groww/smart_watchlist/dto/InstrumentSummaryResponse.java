package com.groww.smart_watchlist.dto;

import com.groww.smart_watchlist.entity.InstrumentType;

// Lightweight reference-data shape for "what stocks/funds exist" — deliberately
// excludes price/NAV (that's MarketDataResponse's job, one click away once the
// user actually adds the instrument). Powers the frontend's global instrument
// search: fetched once and filtered client-side, not re-queried per keystroke.
public record InstrumentSummaryResponse(
        String symbol,
        InstrumentType instrumentType,
        String name,
        String groupLabel // sector for stocks, category for funds
) {
}
