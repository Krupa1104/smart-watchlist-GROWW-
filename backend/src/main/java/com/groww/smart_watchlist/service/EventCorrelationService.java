package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.RelatedEventResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import com.groww.smart_watchlist.entity.MarketEvent;
import com.groww.smart_watchlist.repository.MarketEventRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.Optional;

// Connects a detected anomaly back to one of the dataset's 10 PLANTED
// ground-truth events (data-generator/output/events.json) — deliberately
// NOT a general news/heuristic engine, and NOT modifying events.json,
// load_to_postgres.py, or detection thresholds. With only 10 events across
// 51 instruments over 126 days, most detected anomalies legitimately have
// no matching event; that's an honest, expected outcome (see
// findRelatedEvent's Optional.empty() case), not a gap to hide.
//
// Known, deliberate limitation — sector-scope events: load_to_postgres.py
// stores NULL in market_events.symbol for scope='sector' rows (see
// schema.sql's own comment: "null when scope = 'sector'"), so the sector
// name itself ("IT", "Energy") only survives in the free-text description
// column, not as queryable data. Rather than pattern-matching sector names
// out of free text (fragile, and effectively inventing structured data the
// schema deliberately doesn't store), this service only correlates
// scope=stock and scope=fund events, which carry a real symbol. This mirrors
// an existing, already-documented limitation in this codebase —
// ChangeDetectionServiceGroundTruthTest's disabled
// sectorScopeEventsAreOutOfScopeForInstrumentLevelDetection test — for the
// same underlying reason: sector-scope events have no single symbol to key
// off. A stock in an affected sector (e.g. STK08 during the IT rally) will
// correctly show "no recorded event" rather than a guessed match.
@Service
public class EventCorrelationService {

    // How many calendar days on either side of the detected date still
    // counts as "related" — wide enough to cover a shock's price/volume
    // effect lingering a session or two past the event's own dated row,
    // without being so wide that unrelated events start matching by
    // coincidence (the 10 events are spread roughly every 10-15 days).
    private static final int CORRELATION_WINDOW_DAYS = 3;

    private final MarketEventRepository marketEventRepository;

    public EventCorrelationService(MarketEventRepository marketEventRepository) {
        this.marketEventRepository = marketEventRepository;
    }

    /**
     * Looks for the closest planted event directly on this exact symbol
     * (scope=stock or scope=fund — the only scopes with a real, queryable
     * symbol; see the class-level note on sector-scope events) within
     * {@link #CORRELATION_WINDOW_DAYS} of {@code asOfDate}. Returns the
     * single closest match, or empty if nothing planted is close enough.
     */
    public Optional<RelatedEventResponse> findRelatedEvent(String symbol, InstrumentType instrumentType, LocalDate asOfDate) {
        // instrumentType is accepted (not just symbol) so call sites don't
        // need to know about this limitation — reserved for the day sector
        // correlation becomes possible again (e.g. if the schema starts
        // storing the sector name on sector-scope rows), not used today.
        if (asOfDate == null) {
            return Optional.empty();
        }

        return marketEventRepository.findBySymbol(symbol).stream()
                .filter(e -> withinWindow(e.getEventDate(), asOfDate))
                .min(Comparator.comparingLong(e -> Math.abs(ChronoUnit.DAYS.between(e.getEventDate(), asOfDate))))
                .map(this::toResponse);
    }

    private RelatedEventResponse toResponse(MarketEvent e) {
        return new RelatedEventResponse(e.getEventDate(), e.getScope(), e.getEventType(), e.getDescription());
    }

    private boolean withinWindow(LocalDate eventDate, LocalDate asOfDate) {
        return Math.abs(ChronoUnit.DAYS.between(eventDate, asOfDate)) <= CORRELATION_WINDOW_DAYS;
    }
}
