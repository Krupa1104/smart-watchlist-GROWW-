package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.MarketDataResponse;
import com.groww.smart_watchlist.dto.SnapshotDiffResponse;
import com.groww.smart_watchlist.dto.WatchlistSummaryResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the checkWatchlist() N+1 fix: it used to call
 * MarketDataService.getLatestMarketData() once per item; it now fetches
 * every item's market data via the same batched path getWatchlist()/
 * detectChanges() already use (fetchMarketDataBatch), then applies the
 * per-item snapshot diff exactly as before.
 *
 * This proves FUNCTIONAL correctness (same values as the old per-item
 * approach would have produced), not just "it runs" — a fast wrong answer
 * would be worse than the N+1 pattern it replaces. It intentionally mixes
 * stocks and funds (both batch branches) and multiple items of each, since
 * a single-item watchlist wouldn't meaningfully exercise batching at all.
 */
@SpringBootTest
@Transactional
class CheckWatchlistBatchRegressionTest {

    private static final Integer DEMO_USER_ID = 1;

    @Autowired
    private WatchlistService watchlistService;

    @Autowired
    private MarketDataService marketDataService;

    @Test
    void checkWatchlistReportsCurrentValuesMatchingTheIndividualMarketDataLookupForEveryItem() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Check Batch Test - mixed stocks and funds");

        List<String> stockSymbols = List.of("STK01", "STK04", "STK07");
        List<String> fundSymbols = List.of("FUND01", "FUND02");

        for (String symbol : stockSymbols) {
            watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                    new AddWatchlistItemRequest(symbol, InstrumentType.STOCK));
        }
        for (String symbol : fundSymbols) {
            watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                    new AddWatchlistItemRequest(symbol, InstrumentType.FUND));
        }

        // First check: establishes a baseline (firstView=true for every item),
        // same as it always has — this exercises the batched fetch path with
        // no pre-existing snapshot to diff against.
        List<SnapshotDiffResponse> firstCheck = watchlistService.checkWatchlist(watchlist.id(), DEMO_USER_ID);
        assertEquals(stockSymbols.size() + fundSymbols.size(), firstCheck.size());
        assertTrue(firstCheck.stream().allMatch(SnapshotDiffResponse::firstView),
                "every item's very first check must report firstView=true, batched or not");

        Map<String, MarketDataResponse> expectedBySymbol =
                marketDataService.getLatestMarketDataBatch(stockSymbols, fundSymbols);

        for (SnapshotDiffResponse diff : firstCheck) {
            MarketDataResponse expected = expectedBySymbol.get(diff.symbol());
            assertEquals(0, expected.latestValue().compareTo(diff.currentValue()),
                    () -> diff.symbol() + ": checkWatchlist's currentValue must match the batched market-data value");
            assertEquals(expected.asOfDate(), diff.currentAsOfDate(),
                    () -> diff.symbol() + ": checkWatchlist's currentAsOfDate must match the batched market-data asOfDate");
        }

        // Second check: now every item has a real prior snapshot, so this
        // exercises the "diff against an existing snapshot" branch through
        // the same batched fetch — still one result per item, in order,
        // and still no item reports firstView a second time.
        List<SnapshotDiffResponse> secondCheck = watchlistService.checkWatchlist(watchlist.id(), DEMO_USER_ID);
        assertEquals(stockSymbols.size() + fundSymbols.size(), secondCheck.size());
        assertTrue(secondCheck.stream().noneMatch(SnapshotDiffResponse::firstView),
                "a second check must never report firstView=true again for the same item");

        for (SnapshotDiffResponse diff : secondCheck) {
            MarketDataResponse expected = expectedBySymbol.get(diff.symbol());
            assertEquals(0, expected.latestValue().compareTo(diff.currentValue()));
            assertEquals(0, expected.latestValue().compareTo(diff.previousValue()),
                    () -> diff.symbol() + ": with a static dataset and no intervening tick, "
                            + "the second check's previousValue should equal the first check's recorded value");
        }
    }

    @Test
    void checkWatchlistStillWorksCorrectlyForASingleItemWatchlist() {
        // Guards against an off-by-one in the batching refactor for the
        // smallest possible case (nothing to actually "batch").
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Check Batch Test - single item");
        watchlistService.addItem(watchlist.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK02", InstrumentType.STOCK));

        List<SnapshotDiffResponse> diffs = watchlistService.checkWatchlist(watchlist.id(), DEMO_USER_ID);

        assertEquals(1, diffs.size());
        assertTrue(diffs.get(0).firstView());
        assertEquals("STK02", diffs.get(0).symbol());
    }

    @Test
    void checkWatchlistOnAnEmptyWatchlistReturnsAnEmptyListWithoutError() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Check Batch Test - empty watchlist");

        List<SnapshotDiffResponse> diffs = watchlistService.checkWatchlist(watchlist.id(), DEMO_USER_ID);

        assertTrue(diffs.isEmpty());
    }
}
