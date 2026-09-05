package com.groww.smart_watchlist.dto;

import com.groww.smart_watchlist.entity.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;

// One instrument's current SIMULATED price, pushed over the live SSE feed
// (see TickSimulationService). `simulated` is always true here — carried
// explicitly (not just implied by which endpoint this came from) so the
// frontend can label it correctly wherever this payload ends up, and so
// this can never be mistaken for a real MarketDataResponse if the two ever
// get logged or serialized together.
public record LiveTickResponse(
        String symbol,
        InstrumentType instrumentType,
        BigDecimal value,
        LocalDate asOfDate,
        boolean simulated
) {
}
