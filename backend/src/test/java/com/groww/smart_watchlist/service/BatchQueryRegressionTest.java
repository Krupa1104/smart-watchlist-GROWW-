package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.MarketDataResponse;
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
 * Regression coverage for the remaining two N+1 fixes:
 *  - MarketDataService.getLatestMarketDataBatch (used by getWatchlist())
 *  - WatchlistService.listWatchlists' grouped item-count query
 * Both are verified for functional correctness (same answer as the old
 * per-item approach), not just call-count — a fast wrong answer is worse
 * than a slow correct one.
 */
@SpringBootTest
@Transactional
class BatchQueryRegressionTest {

    private static final Integer DEMO_USER_ID = 1;

    @Autowired
    private MarketDataService marketDataService;

    @Autowired
    private WatchlistService watchlistService;

    @Test
    void batchedMarketDataMatchesPerSymbolLookupExactly() {
        List<String> stockSymbols = List.of("STK01", "STK04", "STK07");
        List<String> fundSymbols = List.of("FUND01", "FUND02");

        Map<String, MarketDataResponse> batched =
                marketDataService.getLatestMarketDataBatch(stockSymbols, fundSymbols);

        for (String symbol : stockSymbols) {
            MarketDataResponse individual = marketDataService.getLatestMarketData(symbol, InstrumentType.STOCK);
            MarketDataResponse fromBatch = batched.get(symbol);
            assertEquals(individual.displayName(), fromBatch.displayName());
            assertEquals(individual.groupLabel(), fromBatch.groupLabel());
            assertEquals(individual.dataAvailable(), fromBatch.dataAvailable());
            assertEquals(individual.asOfDate(), fromBatch.asOfDate());
            // latestValue is a live simulated price on both paths — same
            // symbol, same underlying TickSimulationService state either way,
            // so these should agree exactly (neither call advances the tick).
            assertEquals(0, individual.latestValue().compareTo(fromBatch.latestValue()),
                    () -> symbol + ": batched vs per-symbol latestValue differ");
        }

        for (String symbol : fundSymbols) {
            MarketDataResponse individual = marketDataService.getLatestMarketData(symbol, InstrumentType.FUND);
            MarketDataResponse fromBatch = batched.get(symbol);
            assertEquals(individual.displayName(), fromBatch.displayName());
            assertEquals(individual.groupLabel(), fromBatch.groupLabel());
            assertEquals(0, individual.latestValue().compareTo(fromBatch.latestValue()));
        }
    }

    @Test
    void listWatchlistsReportsCorrectItemCountsAcrossMultipleWatchlists() {
        WatchlistSummaryResponse empty =
                watchlistService.createWatchlist(DEMO_USER_ID, "Batch Count Test - empty");
        WatchlistSummaryResponse withTwo =
                watchlistService.createWatchlist(DEMO_USER_ID, "Batch Count Test - two items");
        watchlistService.addItem(withTwo.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK01", InstrumentType.STOCK));
        watchlistService.addItem(withTwo.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("FUND01", InstrumentType.FUND));

        List<WatchlistSummaryResponse> all = watchlistService.listWatchlists(DEMO_USER_ID);

        WatchlistSummaryResponse emptyResult = all.stream()
                .filter(w -> w.id().equals(empty.id())).findFirst().orElseThrow();
        WatchlistSummaryResponse twoResult = all.stream()
                .filter(w -> w.id().equals(withTwo.id())).findFirst().orElseThrow();

        assertEquals(0, emptyResult.itemCount(), "a watchlist with zero items must report 0, not be missing/null");
        assertEquals(2, twoResult.itemCount());

        // the real seeded demo watchlist (id=1) should still report its
        // real item count too — the grouped query must handle N watchlists
        // of genuinely different sizes in the same call correctly
        assertTrue(all.stream().anyMatch(w -> w.id().equals(1) && w.itemCount() > 0));
    }
}
