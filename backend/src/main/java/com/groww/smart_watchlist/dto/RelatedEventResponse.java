package com.groww.smart_watchlist.dto;

import java.time.LocalDate;

// A planted ground-truth event (see data-generator/output/events.json) that
// falls close enough to a detected change's date to plausibly explain it.
// Deliberately thin — just enough to show/link the underlying event, not a
// re-interpretation of it. Absent (null) is a real, expected outcome: with
// only 10 planted events across 51 instruments over 126 days, most detected
// anomalies legitimately have no matching event — see EventCorrelationService.
public record RelatedEventResponse(
        LocalDate eventDate,
        String scope, // "stock" | "sector" | "fund"
        String eventType,
        String description
) {
}
