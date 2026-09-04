package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.AttentionItemResponse;
import com.groww.smart_watchlist.dto.WatchlistItemResponse;
import com.groww.smart_watchlist.dto.WatchlistSummaryResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 4 tests. No fixtures/fake data: these run against the real seeded
 * demo user (id=1) and demo watchlist (id=1, "first 8 stocks + first 4
 * funds" from load_to_postgres.py) over whatever the actual latest trading
 * day in the loaded dataset shows.
 *
 * Before writing this test, the dataset was inspected directly (see the
 * chat/session notes) and confirmed to already contain two organic,
 * non-planted anomalies on its latest date — no synthetic event needed:
 *   - STK04: real return z-score of ~4.5 vs its own trailing 20-day history
 *     (return_z_score trigger; volume ratio ~1.6x stays under the 2.0x
 *     volume threshold, so only the return signal fires)
 *   - FUND02: real ~1.2 percentage-point NAV deviation vs its Large Cap
 *     peers (category has only 3 funds, under MIN_CATEGORY_SAMPLE_FOR_ZSCORE,
 *     so this trips the flat percentage-point fallback, not a z-score)
 * The other 7 stocks and 3 funds on the demo watchlist stay under both
 * thresholds on that same date. This test class is @Transactional so
 * anything it triggers ChangeDetectionService to persist to detected_changes,
 * and any watchlist it creates, are rolled back after each test — the real
 * dataset itself is never modified.
 */
@SpringBootTest
@Transactional
class WatchlistAttentionTest {

    private static final Integer DEMO_USER_ID = 1;
    private static final Integer DEMO_WATCHLIST_ID = 1;

    @Autowired
    private WatchlistService watchlistService;

    @Test
    void demoWatchlistAttentionListContainsOnlyTheMeaningfulItems() {
        List<AttentionItemResponse> attention =
                watchlistService.getAttentionItems(DEMO_WATCHLIST_ID, DEMO_USER_ID);

        // Exactly the two organically-anomalous instruments, nothing else —
        // this is simultaneously "meaningful changes are returned" and
        // "normal instruments are excluded", since the demo watchlist has
        // 12 items total and only these 2 clear a threshold.
        List<String> symbols = attention.stream().map(AttentionItemResponse::symbol).toList();
        assertEquals(List.of("STK04", "FUND02"), symbols,
                "expected exactly STK04 and FUND02 to be attention-worthy on the current latest date; got: " + symbols);
    }

    @Test
    void attentionListIsOrderedBySeverityDescending() {
        List<AttentionItemResponse> attention =
                watchlistService.getAttentionItems(DEMO_WATCHLIST_ID, DEMO_USER_ID);

        assertTrue(attention.size() >= 2, "need at least 2 attention items to verify ordering");
        for (int i = 0; i < attention.size() - 1; i++) {
            BigDecimal current = attention.get(i).severity();
            BigDecimal next = attention.get(i + 1).severity();
            assertTrue(current.compareTo(next) >= 0,
                    () -> "expected descending severity but got " + current + " before " + next);
        }
    }

    @Test
    void attentionItemContainsAllFieldsAnAttentionCardNeeds() {
        List<AttentionItemResponse> attention =
                watchlistService.getAttentionItems(DEMO_WATCHLIST_ID, DEMO_USER_ID);

        AttentionItemResponse stk04 = attention.stream()
                .filter(a -> a.symbol().equals("STK04"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("STK04 should be in the attention list"));

        assertEquals(InstrumentType.STOCK, stk04.instrumentType());
        assertNotNull(stk04.asOfDate(), "asOfDate should be populated");
        assertNotNull(stk04.changeType(), "changeType should be populated");
        assertNotNull(stk04.severity(), "severity should be populated");
        assertTrue(stk04.severity().compareTo(BigDecimal.ZERO) > 0, "severity should be a positive magnitude");
        assertNotNull(stk04.explanation(), "explanation should be populated");
        assertFalse(stk04.explanation().isBlank());
        assertTrue(stk04.metrics().containsKey("returnZScore"),
                "underlying Phase 3 metrics (returnZScore) should be carried through for transparency");
    }

    @Test
    void watchlistWithOnlyNormalInstrumentsReturnsEmptyAttentionList() {
        // Real watchlist created through the normal service API (not a raw
        // insert), holding one real, currently-normal symbol (STK01: z~1.0,
        // volume ratio ~0.6x — both well under threshold, confirmed by
        // inspecting the loaded dataset directly). Rolled back by
        // @Transactional at the end of the test; the seeded demo watchlist
        // is left untouched.
        WatchlistSummaryResponse quietWatchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Attention Test - all quiet");

        WatchlistItemResponse addedItem = watchlistService.addItem(
                quietWatchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK01", InstrumentType.STOCK));
        assertNotNull(addedItem);

        List<AttentionItemResponse> attention =
                watchlistService.getAttentionItems(quietWatchlist.id(), DEMO_USER_ID);

        assertTrue(attention.isEmpty(),
                "expected no attention items for a watchlist containing only a currently-normal symbol; got: " + attention);
    }
}
