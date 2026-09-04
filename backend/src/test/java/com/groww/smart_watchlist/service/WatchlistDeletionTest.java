package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.WatchlistSummaryResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import com.groww.smart_watchlist.exception.ResourceNotFoundException;
import com.groww.smart_watchlist.exception.UnauthorizedAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Delete-watchlist tests. Like WatchlistAttentionTest, these run against the
 * real seeded demo user (id=1) but never touch the seeded demo watchlist
 * (id=1) itself — every watchlist deleted here is created by the test
 * through the normal service API first. @Transactional rolls everything
 * back afterward regardless, but the assertions below check the delete
 * actually took effect *within* the test, before that rollback happens.
 */
@SpringBootTest
@Transactional
class WatchlistDeletionTest {

    private static final Integer DEMO_USER_ID = 1;
    private static final Integer OTHER_USER_ID = 999; // no such user is seeded — used only to prove ownership is enforced

    @Autowired
    private WatchlistService watchlistService;

    @Test
    void deletingAWatchlistRemovesItAndItsItems() {
        WatchlistSummaryResponse created =
                watchlistService.createWatchlist(DEMO_USER_ID, "Delete Test - basic");
        watchlistService.addItem(created.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK01", InstrumentType.STOCK));

        watchlistService.deleteWatchlist(created.id(), DEMO_USER_ID);

        assertThrows(ResourceNotFoundException.class,
                () -> watchlistService.getWatchlist(created.id(), DEMO_USER_ID),
                "watchlist should no longer be loadable after deletion");

        List<Integer> remainingIds = watchlistService.listWatchlists(DEMO_USER_ID).stream()
                .map(WatchlistSummaryResponse::id)
                .toList();
        assertFalse(remainingIds.contains(created.id()),
                "deleted watchlist should no longer appear in the user's list");
    }

    @Test
    void deletingSomeoneElsesWatchlistIsRejected() {
        WatchlistSummaryResponse created =
                watchlistService.createWatchlist(DEMO_USER_ID, "Delete Test - ownership");

        assertThrows(UnauthorizedAccessException.class,
                () -> watchlistService.deleteWatchlist(created.id(), OTHER_USER_ID),
                "a watchlist owned by DEMO_USER_ID must not be deletable by a different userId");

        // still there — the rejected attempt must not have deleted anything
        assertTrue(watchlistService.listWatchlists(DEMO_USER_ID).stream()
                .anyMatch(w -> w.id().equals(created.id())));
    }

    @Test
    void deletingANonexistentWatchlistIs404() {
        assertThrows(ResourceNotFoundException.class,
                () -> watchlistService.deleteWatchlist(999_999, DEMO_USER_ID));
    }

    @Test
    void deletingOneWatchlistDoesNotAffectAnother() {
        WatchlistSummaryResponse keep =
                watchlistService.createWatchlist(DEMO_USER_ID, "Delete Test - keep me");
        WatchlistSummaryResponse discard =
                watchlistService.createWatchlist(DEMO_USER_ID, "Delete Test - discard me");
        watchlistService.addItem(keep.id(), DEMO_USER_ID,
                new AddWatchlistItemRequest("STK02", InstrumentType.STOCK));

        watchlistService.deleteWatchlist(discard.id(), DEMO_USER_ID);

        assertEquals(1, watchlistService.getWatchlist(keep.id(), DEMO_USER_ID).items().size(),
                "an unrelated watchlist's items must be unaffected by deleting a different watchlist");
    }
}