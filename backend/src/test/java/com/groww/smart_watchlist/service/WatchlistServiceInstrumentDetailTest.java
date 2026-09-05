package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.InstrumentDetailResponse;
import com.groww.smart_watchlist.dto.WatchlistSummaryResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import com.groww.smart_watchlist.exception.ResourceNotFoundException;
import com.groww.smart_watchlist.exception.UnauthorizedAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration tests for WatchlistService.getInstrumentDetail() — the new
 * instrument detail panel endpoint. Runs against the real seeded demo user
 * (id=1); every watchlist used here is created by the test through the
 * normal service API first, same convention as WatchlistDeletionTest /
 * WatchlistAttentionTest. @Transactional rolls everything back afterward.
 */
@SpringBootTest
@Transactional
class WatchlistServiceInstrumentDetailTest {

    private static final Integer DEMO_USER_ID = 1;
    private static final Integer OTHER_USER_ID = 999; // no such user is seeded

    @Autowired
    private WatchlistService watchlistService;

    @Test
    void detailResponseCarriesTheCoreInstrumentAndMarketDataFields() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Detail Test - basics");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK01", InstrumentType.STOCK));

        InstrumentDetailResponse detail = watchlistService.getInstrumentDetail(watchlist.id(), DEMO_USER_ID, "STK01");

        assertEquals("STK01", detail.symbol());
        assertEquals(InstrumentType.STOCK, detail.instrumentType());
        assertNotNull(detail.marketData());
        assertEquals("STK01", detail.marketData().symbol());
        assertTrue(detail.marketData().dataAvailable());
        assertNotNull(detail.detectedChange(), "a verdict — meaningful or not — should always be present");
    }

    @Test
    void recentHistoryIsChronologicalAndNonEmpty() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Detail Test - history");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK01", InstrumentType.STOCK));

        InstrumentDetailResponse detail = watchlistService.getInstrumentDetail(watchlist.id(), DEMO_USER_ID, "STK01");

        assertFalse(detail.recentHistory().isEmpty(), "expected some recent history for a seeded stock");
        for (int i = 0; i < detail.recentHistory().size() - 1; i++) {
            assertTrue(
                    detail.recentHistory().get(i).date().isBefore(detail.recentHistory().get(i + 1).date()),
                    "recent history should be ordered oldest-first for charting"
            );
        }
    }

    @Test
    void sinceLastCheckIsFirstViewWhenTheItemHasNeverBeenChecked() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Detail Test - first view");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK02", InstrumentType.STOCK));

        InstrumentDetailResponse detail = watchlistService.getInstrumentDetail(watchlist.id(), DEMO_USER_ID, "STK02");

        assertTrue(detail.sinceLastCheck().firstView(), "a never-checked item should report firstView=true");
        assertNull(detail.sinceLastCheck().previousValue());
    }

    @Test
    void viewingTheDetailPanelNeverCreatesOrMovesASnapshot() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Detail Test - read only");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK03", InstrumentType.STOCK));

        // Viewing detail twice in a row must not itself create a snapshot —
        // only the explicit /check action is allowed to do that.
        InstrumentDetailResponse first = watchlistService.getInstrumentDetail(watchlist.id(), DEMO_USER_ID, "STK03");
        InstrumentDetailResponse second = watchlistService.getInstrumentDetail(watchlist.id(), DEMO_USER_ID, "STK03");

        assertTrue(first.sinceLastCheck().firstView());
        assertTrue(second.sinceLastCheck().firstView(),
                "merely viewing the detail panel must not create a snapshot — firstView should still be true");
    }

    @Test
    void sinceLastCheckReflectsAPriorExplicitCheck() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Detail Test - after check");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK04", InstrumentType.STOCK));

        watchlistService.checkWatchlist(watchlist.id(), DEMO_USER_ID); // records a real snapshot

        InstrumentDetailResponse detail = watchlistService.getInstrumentDetail(watchlist.id(), DEMO_USER_ID, "STK04");

        assertFalse(detail.sinceLastCheck().firstView(), "a checked item should no longer report firstView=true");
        assertNotNull(detail.sinceLastCheck().previousValue());
        assertNotNull(detail.sinceLastCheck().previousViewedAt());
        // with only one price point ever recorded as "seen", previous should equal current
        assertEquals(0, detail.sinceLastCheck().previousValue()
                .compareTo(detail.sinceLastCheck().currentValue()));
    }

    @Test
    void suggestedActionsAreEmptyWhenTheDetectedChangeIsNotMeaningful() {
        // STK01 is confirmed quiet on the dataset's latest date (see
        // WatchlistAttentionTest) — z~1.0, well under threshold.
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Detail Test - quiet instrument");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK01", InstrumentType.STOCK));

        InstrumentDetailResponse detail = watchlistService.getInstrumentDetail(watchlist.id(), DEMO_USER_ID, "STK01");

        assertFalse(detail.detectedChange().meaningful());
        assertTrue(detail.suggestedActions().isEmpty(),
                "a normal, non-meaningful move should have no suggested actions");
    }

    @Test
    void suggestedActionsArePopulatedWhenTheDetectedChangeIsMeaningful() {
        // STK04 has a real organic anomaly on the dataset's latest date
        // (see WatchlistAttentionTest) — return z-score ~4.5.
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Detail Test - meaningful instrument");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK04", InstrumentType.STOCK));

        InstrumentDetailResponse detail = watchlistService.getInstrumentDetail(watchlist.id(), DEMO_USER_ID, "STK04");

        assertTrue(detail.detectedChange().meaningful());
        assertFalse(detail.suggestedActions().isEmpty(),
                "a meaningful move should always carry at least one suggested action");
        // the dataset's latest date is nowhere near any of the 10 planted
        // events, so the honest, expected outcome here is "no related event"
        assertNull(detail.relatedEvent());
        assertTrue(detail.suggestedActions().stream()
                        .anyMatch(s -> s.toLowerCase().contains("no recorded event")),
                "with no related event, suggestions should say so explicitly rather than imply one exists");
        // priorDetectionCount wires through getInstrumentDetail() end-to-end
        // (see DetectedChangeAuditTrailTest for the dedup behavior itself)
        assertTrue(detail.priorDetectionCount() >= 1,
                "detecting STK04 here should have just recorded (or already have recorded) at least one prior detection");
    }

    @Test
    void rejectsAWatchlistBelongingToADifferentUser() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Detail Test - ownership");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK01", InstrumentType.STOCK));

        assertThrows(UnauthorizedAccessException.class,
                () -> watchlistService.getInstrumentDetail(watchlist.id(), OTHER_USER_ID, "STK01"));
    }

    @Test
    void rejectsASymbolThatIsNotOnTheWatchlist() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Detail Test - missing symbol");

        assertThrows(ResourceNotFoundException.class,
                () -> watchlistService.getInstrumentDetail(watchlist.id(), DEMO_USER_ID, "STK01"));
    }
}
