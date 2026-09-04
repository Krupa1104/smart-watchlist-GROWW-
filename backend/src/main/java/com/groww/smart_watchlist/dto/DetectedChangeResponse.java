package com.groww.smart_watchlist.dto;

import com.groww.smart_watchlist.entity.InstrumentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

// meaningful=false is a real, expected outcome — it's the "everything else
// moved normally, no action needed" half of the product thesis, not an
// error case. metrics carries the raw numbers behind the verdict (z-score,
// volume ratio, category deviation, sample size) so this is demoable and
// explainable live, not a black box — matters for the Present round's
// "explain the trade-offs" requirement.
public record DetectedChangeResponse(
        String symbol,
        InstrumentType instrumentType,
        LocalDate asOfDate,
        boolean meaningful,
        String changeType,
        BigDecimal severityScore,
        String explanation,
        Map<String, BigDecimal> metrics
) {
}
