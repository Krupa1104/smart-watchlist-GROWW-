package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.SnapshotDiffResponse;
import com.groww.smart_watchlist.dto.WatchlistSummaryResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for a real race in checkWatchlist()/SnapshotService:
 * two concurrent "Check for changes" calls for the SAME never-before-checked
 * item could both read "no snapshot exists" before either wrote one, then
 * both attempt to insert — the second failing on watchlist_snapshots'
 * UNIQUE(watchlist_item_id) constraint. Fixed by having checkWatchlist()
 * acquire a row lock on the WatchlistItem (findByIdForUpdate) before
 * reading/writing its snapshot, serializing concurrent checks on the same
 * item so the second always correctly sees the first's committed write.
 *
 * Deliberately NOT @Transactional at the class level: a real concurrency
 * test needs the setup (creating the watchlist/item) to actually COMMIT so
 * the separate threads below — each getting their own real transaction —
 * can see it. Cleanup is manual (see tearDown) instead of relying on
 * Spring's test-transaction rollback.
 */
@SpringBootTest
class SnapshotConcurrencyTest {

    private static final Integer DEMO_USER_ID = 1;

    @Autowired
    private WatchlistService watchlistService;

    private Integer watchlistId;

    @AfterEach
    void tearDown() {
        if (watchlistId != null) {
            watchlistService.deleteWatchlist(watchlistId, DEMO_USER_ID);
            watchlistId = null;
        }
    }

    @Test
    void concurrentFirstChecksOnTheSameItemAreCorrectlySerialized() throws Exception {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Concurrency Test - first check race");
        watchlistId = watchlist.id();
        watchlistService.addItem(watchlistId, DEMO_USER_ID,
                new AddWatchlistItemRequest("STK01", InstrumentType.STOCK));

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<SnapshotDiffResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return watchlistService.checkWatchlist(watchlistId, DEMO_USER_ID);
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown(); // release all threads to call checkWatchlist() at (as close to) the same instant as possible

        List<SnapshotDiffResponse> allDiffs = new ArrayList<>();
        for (Future<List<SnapshotDiffResponse>> future : futures) {
            List<SnapshotDiffResponse> diffs = future.get(15, TimeUnit.SECONDS); // no exception = no unhandled constraint violation
            assertEquals(1, diffs.size(), "each concurrent check should still see exactly one item");
            allDiffs.add(diffs.get(0));
        }
        pool.shutdown();

        long firstViewCount = allDiffs.stream().filter(SnapshotDiffResponse::firstView).count();
        assertEquals(1, firstViewCount,
                "with correct locking, EXACTLY ONE of the concurrent first-ever checks should win and "
                        + "report firstView=true; the rest must correctly see it as already checked. Got: " + allDiffs);

        long notFirstViewCount = allDiffs.stream().filter(d -> !d.firstView()).count();
        assertEquals(threadCount - 1, notFirstViewCount);
    }

    @Test
    void concurrentSubsequentChecksNeverThrowAndAlwaysAgreeOnAPreviousValue() throws Exception {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Concurrency Test - subsequent check race");
        watchlistId = watchlist.id();
        watchlistService.addItem(watchlistId, DEMO_USER_ID,
                new AddWatchlistItemRequest("STK02", InstrumentType.STOCK));

        // Establish a real baseline first, so every racing call below is a
        // "subsequent" check (firstView=false already), the more common
        // real-world case (e.g. two tabs open on an already-used watchlist).
        watchlistService.checkWatchlist(watchlistId, DEMO_USER_ID);

        int threadCount = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<List<SnapshotDiffResponse>>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                go.await();
                return watchlistService.checkWatchlist(watchlistId, DEMO_USER_ID);
            }));
        }

        ready.await(5, TimeUnit.SECONDS);
        go.countDown();

        for (Future<List<SnapshotDiffResponse>> future : futures) {
            List<SnapshotDiffResponse> diffs = future.get(15, TimeUnit.SECONDS);
            assertEquals(1, diffs.size());
            SnapshotDiffResponse diff = diffs.get(0);
            assertTrue(!diff.firstView(), "a baseline already existed before the race started");
            assertTrue(diff.previousValue() != null && diff.currentValue() != null,
                    "a correctly-serialized check must always have both a previous and a current value");
        }
        pool.shutdown();
    }
}
