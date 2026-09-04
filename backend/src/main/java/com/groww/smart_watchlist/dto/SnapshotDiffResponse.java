package com.groww.smart_watchlist.dto;

import com.groww.smart_watchlist.entity.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

// This is the RAW diff — previous snapshot value vs current value. It says
// nothing about whether the move is "meaningful" (that's ChangeDetectionService,
// Phase 3, which will consume this same shape and add z-scores/category
// comparisons on top). previousValue/previousViewedAt are null on firstView
// (no baseline exists yet — nothing to compare against, not a zero move).
public record SnapshotDiffResponse(
        Integer itemId,
        String symbol,
        InstrumentType instrumentType,
        BigDecimal previousValue,
        OffsetDateTime previousViewedAt,
        BigDecimal currentValue,
        LocalDate currentAsOfDate,
        boolean firstView,
        boolean dataAvailable // false if there's no current price/NAV at all — snapshot untouched
) {
}
