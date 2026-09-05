package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.RelatedEventResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates EventCorrelationService against the real 10 planted events in
 * data-generator/output/events.json (loaded into market_events by
 * load_to_postgres.py) — no fixtures, same convention as
 * ChangeDetectionServiceGroundTruthTest. Requires a running Postgres with
 * the dataset loaded, same as the app itself.
 */
@SpringBootTest
class EventCorrelationServiceTest {

    @Autowired
    private EventCorrelationService eventCorrelationService;

    @Test
    void findsADirectStockEventOnItsExactPlantedDate() {
        // STK07 earnings_beat, events.json day 20
        Optional<RelatedEventResponse> related =
                eventCorrelationService.findRelatedEvent("STK07", InstrumentType.STOCK, LocalDate.of(2026, 3, 30));

        assertTrue(related.isPresent(), "STK07 has a directly planted event on 2026-03-30");
        assertEquals("earnings_beat", related.get().eventType());
        assertEquals("stock", related.get().scope());
    }

    @Test
    void findsADirectStockEventWithinTheCorrelationWindowButNotOnTheExactDate() {
        // Same STK07 earnings_beat, asked about 2 calendar days later —
        // still within the correlation window.
        Optional<RelatedEventResponse> related =
                eventCorrelationService.findRelatedEvent("STK07", InstrumentType.STOCK, LocalDate.of(2026, 4, 1));

        assertTrue(related.isPresent(), "an event 2 days prior should still be within the correlation window");
        assertEquals("earnings_beat", related.get().eventType());
    }

    @Test
    void findsADirectFundEvent() {
        // FUND04 manager_change, events.json day 40
        Optional<RelatedEventResponse> related =
                eventCorrelationService.findRelatedEvent("FUND04", InstrumentType.FUND, LocalDate.of(2026, 4, 27));

        assertTrue(related.isPresent(), "FUND04 has a directly planted event on 2026-04-27");
        assertEquals("manager_change", related.get().eventType());
        assertEquals("fund", related.get().scope());
    }

    @Test
    void sectorScopeEventsAreNotCorrelatedToMemberStocks_knownLimitation() {
        // STK08 is IT-sector (see stocks.json) with no direct event of its
        // own. The only planted event near 2026-05-25 is the sector-wide
        // "IT sector rallies" event — but load_to_postgres.py stores NULL
        // in market_events.symbol for scope='sector' rows (see schema.sql:
        // "null when scope = 'sector'"), so the sector name itself isn't
        // queryable data. This is a documented, deliberate limitation (see
        // EventCorrelationService's class javadoc and the existing disabled
        // test in ChangeDetectionServiceGroundTruthTest for the same
        // underlying reason) — not a bug to fix here.
        Optional<RelatedEventResponse> related =
                eventCorrelationService.findRelatedEvent("STK08", InstrumentType.STOCK, LocalDate.of(2026, 5, 25));

        assertTrue(related.isEmpty(),
                "sector-scope events have no queryable symbol in the loaded schema — "
                        + "a member stock with no direct event of its own correctly shows no related event");
    }

    @Test
    void returnsEmptyWhenNoPlantedEventIsAnywhereNearThatDate() {
        // STK01 has no direct event at all, and 2026-04-01 is far from
        // every planted event's window (nearest is STK07 on 2026-03-30,
        // but STK01 != STK07 and they don't share a sector-scope event here).
        Optional<RelatedEventResponse> related =
                eventCorrelationService.findRelatedEvent("STK01", InstrumentType.STOCK, LocalDate.of(2026, 4, 1));

        assertTrue(related.isEmpty(),
                "STK01 on 2026-04-01 should have no related event — this is the expected, honest 'no event' case");
    }

    @Test
    void returnsEmptyForANullAsOfDate() {
        Optional<RelatedEventResponse> related =
                eventCorrelationService.findRelatedEvent("STK01", InstrumentType.STOCK, null);

        assertTrue(related.isEmpty(), "no as-of date means nothing to correlate against");
    }
}
