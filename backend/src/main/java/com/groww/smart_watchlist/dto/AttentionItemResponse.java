package com.groww.smart_watchlist.dto;

import com.groww.smart_watchlist.entity.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

// Deliberately a separate type from DetectedChangeResponse, even though the
// fields largely overlap — this is the public "attention card" contract the
// frontend renders directly, and it drops the internal `meaningful` flag
// (every item in an attention list is meaningful by construction, so
// repeating that boolean on each entry would just be noise). Keeping it
// separate also means Phase 3's internal DTO can evolve without silently
// changing this API's shape.
public record AttentionItemResponse(
        String symbol,
        InstrumentType instrumentType,
        LocalDate asOfDate,
        String changeType,
        BigDecimal severity,
        String explanation,
        Map<String, BigDecimal> metrics
) {
}
