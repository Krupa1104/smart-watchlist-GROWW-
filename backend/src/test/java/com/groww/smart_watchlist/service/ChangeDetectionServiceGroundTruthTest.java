package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.DetectedChangeResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates ChangeDetectionService against the 10 ground-truth events
 * deliberately planted by data-generator/generate_market_data.py, recorded
 * in data-generator/output/events.json.
 *
 * Uses {@link ChangeDetectionService#evaluateAsOf} (not the production
 * {@code detect}/{@code /api/watchlists/{id}/detect} path) so each event is
 * evaluated as if its own planted date were "today", using only the data
 * available up to and including that date — and without writing anything
 * into detected_changes. See ChangeDetectionService and StockPriceRepository
 * / FundNavRepository for how the historical-date queries are kept
 * equivalent to the production "latest" ones.
 *
 * Requires a running Postgres with the dataset loaded (schema.sql +
 * load_to_postgres.py) and DB_PASSWORD set, same as running the app itself.
 */
@SpringBootTest
class ChangeDetectionServiceGroundTruthTest {

    @Autowired
    private ChangeDetectionService changeDetectionService;

    // The 6 stock-scope events from events.json. Each has a large enough
    // price_shock (5-15%) and volume_mult (2.4x-5.0x) that both signals this
    // detector checks — return z-score vs the stock's own history, and
    // volume vs its own 20-day average — should clear their thresholds
    // (STOCK_RETURN_Z_THRESHOLD=2.0, VOLUME_SPIKE_RATIO_THRESHOLD=2.0x).
    static Stream<Arguments> plantedStockEvents() {
        return Stream.of(
                Arguments.of("STK07", LocalDate.of(2026, 3, 30), "earnings_beat"),
                Arguments.of("STK14", LocalDate.of(2026, 4, 17), "earnings_miss"),
                Arguments.of("STK03", LocalDate.of(2026, 5, 7), "regulatory_action"),
                Arguments.of("STK28", LocalDate.of(2026, 6, 10), "insider_buying"),
                Arguments.of("STK32", LocalDate.of(2026, 7, 13), "product_launch"),
                Arguments.of("STK19", LocalDate.of(2026, 8, 3), "guidance_cut")
        );
    }

    @ParameterizedTest(name = "{2}: {0} on {1} is flagged meaningful")
    @MethodSource("plantedStockEvents")
    void plantedStockShocksAreDetectedAsMeaningful(String symbol, LocalDate eventDate, String eventType) {
        DetectedChangeResponse result =
                changeDetectionService.evaluateAsOf(symbol, InstrumentType.STOCK, eventDate);

        assertTrue(result.meaningful(),
                () -> eventType + " (" + symbol + " on " + eventDate
                        + ") was NOT flagged as meaningful. Metrics: " + result.metrics());
        assertNotNull(result.changeType(), "a meaningful verdict should always carry a changeType");
        assertNotNull(result.severityScore(), "a meaningful verdict should always carry a severityScore");
    }

    /**
     * Documented limitation: the two planted fund events (FUND04
     * manager_change, FUND10 rating_upgrade) have price_shock=0.0 and
     * volume_mult=1.0 in events.json — the generator deliberately gave them
     * no numeric footprint, since these are qualitative/announcement-type
     * events. A NAV-vs-category-peers detector has nothing to key off here
     * by design, so these are expected to be MISSED, not caught. That's a
     * real gap (closing it would need the announcement text itself, not
     * more numeric tuning) rather than a bug in this detector.
     */
    @Test
    void plantedFundEventsWithNoPriceFootprintAreNotDetected_knownLimitation() {
        DetectedChangeResponse fund04 = changeDetectionService.evaluateAsOf(
                "FUND04", InstrumentType.FUND, LocalDate.of(2026, 4, 27)); // manager_change
        DetectedChangeResponse fund10 = changeDetectionService.evaluateAsOf(
                "FUND10", InstrumentType.FUND, LocalDate.of(2026, 7, 27)); // rating_upgrade

        assertFalse(fund04.meaningful(),
                "FUND04 manager_change has 0% price_shock / 1x volume by design — "
                        + "expected to be missed by a numeric detector. Metrics: " + fund04.metrics());
        assertFalse(fund10.meaningful(),
                "FUND10 rating_upgrade has 0% price_shock / 1x volume by design — "
                        + "expected to be missed by a numeric detector. Metrics: " + fund10.metrics());
    }

    /**
     * Documented limitation: the two planted sector-scope events (IT rally
     * on 2026-05-25, Energy selloff on 2026-06-29) are keyed by SECTOR name
     * ("IT", "Energy"), not an individual stock/fund symbol. This detector
     * is instrument-level only — evaluateAsOf takes one symbol and compares
     * it to its own history (stocks) or its own category peers (funds); it
     * has no sector-level aggregation step, so there's no single symbol to
     * point it at for a sector-scope event. Disabled rather than silently
     * omitted, so the gap stays visible until sector-level detection (or an
     * "apply the sector move to its member stocks" check) is built —
     * that's future scope, not part of Phase 3.
     */
    @Test
    @Disabled("Sector-scope events (IT rally 2026-05-25, Energy selloff 2026-06-29) have no single "
            + "symbol to evaluate against — this detector is instrument-level only. See class javadoc.")
    void sectorScopeEventsAreOutOfScopeForInstrumentLevelDetection() {
        // Intentionally left unimplemented — this test documents a
        // structural limitation, not a bug to fix within Phase 3.
    }
}
