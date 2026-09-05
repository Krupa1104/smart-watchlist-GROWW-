package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.SnapshotDiffResponse;
import com.groww.smart_watchlist.dto.WatchlistItemResponse;
import com.groww.smart_watchlist.dto.WatchlistSummaryResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import com.groww.smart_watchlist.entity.WatchlistItem;
import com.groww.smart_watchlist.entity.WatchlistSnapshot;
import com.groww.smart_watchlist.repository.WatchlistSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for a reported bug: "since last check" showed
 * previousValue == currentValue (a flat +₹0.00 / 0.00% row) for every
 * instrument. Root-caused as NOT a code bug — SnapshotService.recordCheck()
 * already reads the snapshot's old value into an immutable BigDecimal
 * BEFORE overwriting it (see its own comment: "diffs... THEN overwrites").
 * The observed screenshot was against the real seeded demo watchlist
 * (id=1), which the project's own earlier status notes confirm was
 * ALREADY checked once before ("12 snapshots exist for the seeded 12
 * watchlist items... snapshots persist across restart") — so on this
 * still-static dataset (no live/intraday feed yet), a second check
 * necessarily compares the same frozen value against itself: 0% is the
 * mathematically correct answer for an already-checked item whose
 * underlying data hasn't moved, not a sign the diff math is broken.
 *
 * This test proves the diff math itself is correct regardless of history:
 * it manually seeds an ARTIFICIALLY stale snapshot value (as if "the last
 * check saw a different number"), then confirms checkWatchlist() reports
 * exactly that old value as previousValue against today's real current
 * value — covering both a positive and a negative movement, per the bug
 * report's explicit ask.
 */
@SpringBootTest
@Transactional
class SnapshotDiffAccuracyTest {

    private static final Integer DEMO_USER_ID = 1;

    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private WatchlistSnapshotRepository watchlistSnapshotRepository;

    @Test
    void reportsAPositiveMoveAgainstAnArtificiallyStalePreviousValue() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Snapshot Diff Test - positive move");
        WatchlistItemResponse added = watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK04", InstrumentType.STOCK));

        BigDecimal realCurrentValue = added.marketData().latestValue(); // 245.91 in the seeded dataset
        BigDecimal staleValue = realCurrentValue.subtract(BigDecimal.valueOf(14.09)); // simulate an older, lower value
        seedStaleSnapshot(added.itemId(), staleValue);

        List<SnapshotDiffResponse> diffs = watchlistService.checkWatchlist(watchlist.id(), DEMO_USER_ID);
        SnapshotDiffResponse diff = onlyDiff(diffs);

        assertFalse(diff.firstView(), "a seeded snapshot means this is not a first view");
        assertEquals(0, diff.previousValue().compareTo(staleValue),
                "previousValue must be the OLD (stale) snapshot value, not today's current value");
        assertEquals(0, diff.currentValue().compareTo(realCurrentValue),
                "currentValue must be today's real market value");
        // the two must actually differ — this is the exact regression: previousValue == currentValue for every row
        assertFalse(diff.previousValue().compareTo(diff.currentValue()) == 0,
                "previousValue and currentValue must NOT be equal when the snapshot was genuinely different");

        BigDecimal absChange = diff.currentValue().subtract(diff.previousValue());
        assertEquals(0, absChange.compareTo(BigDecimal.valueOf(14.09)),
                "expected a positive +14.09 move; got " + absChange);
    }

    @Test
    void reportsANegativeMoveAgainstAnArtificiallyStalePreviousValue() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Snapshot Diff Test - negative move");
        WatchlistItemResponse added = watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK01", InstrumentType.STOCK));

        BigDecimal realCurrentValue = added.marketData().latestValue();
        BigDecimal staleValue = realCurrentValue.add(BigDecimal.valueOf(50)); // simulate an older, HIGHER value
        seedStaleSnapshot(added.itemId(), staleValue);

        List<SnapshotDiffResponse> diffs = watchlistService.checkWatchlist(watchlist.id(), DEMO_USER_ID);
        SnapshotDiffResponse diff = onlyDiff(diffs);

        assertEquals(0, diff.previousValue().compareTo(staleValue));
        assertEquals(0, diff.currentValue().compareTo(realCurrentValue));

        BigDecimal absChange = diff.currentValue().subtract(diff.previousValue());
        assertTrue(absChange.compareTo(BigDecimal.ZERO) < 0,
                "expected a negative move; got " + absChange);
        assertEquals(0, absChange.compareTo(BigDecimal.valueOf(-50)));
    }

    @Test
    void secondCheckOfAnAlreadyCheckedItemCorrectlyShowsZeroWhenNothingChanged() {
        // The counterpart case: this is what the reported screenshot actually
        // shows, and it IS correct — not a bug. Checking an item twice in a
        // row, with no live feed varying the underlying value between
        // checks, must legitimately report a flat 0% move both times.
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Snapshot Diff Test - genuinely unchanged");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK02", InstrumentType.STOCK));

        List<SnapshotDiffResponse> first = watchlistService.checkWatchlist(watchlist.id(), DEMO_USER_ID);
        assertTrue(onlyDiff(first).firstView(), "first check should have no baseline yet");

        List<SnapshotDiffResponse> second = watchlistService.checkWatchlist(watchlist.id(), DEMO_USER_ID);
        SnapshotDiffResponse diff = onlyDiff(second);

        assertFalse(diff.firstView());
        assertEquals(0, diff.previousValue().compareTo(diff.currentValue()),
                "with a static dataset and no value change between checks, 0% is the correct answer");
    }

    private void seedStaleSnapshot(Integer watchlistItemId, BigDecimal staleValue) {
        WatchlistSnapshot snapshot = new WatchlistSnapshot();
        // WatchlistItem association is set via the id field directly to
        // avoid re-fetching the managed entity — recordCheck() only reads
        // getLastSeenValue()/getLastViewedAt() off whatever row this join
        // column resolves to, so this is sufficient to simulate "an earlier,
        // different check happened".
        WatchlistItem itemRef = new WatchlistItem();
        itemRef.setId(watchlistItemId);
        snapshot.setWatchlistItem(itemRef);
        snapshot.setLastSeenValue(staleValue);
        watchlistSnapshotRepository.save(snapshot);
    }

    private SnapshotDiffResponse onlyDiff(List<SnapshotDiffResponse> diffs) {
        assertEquals(1, diffs.size());
        return diffs.get(0);
    }
}
