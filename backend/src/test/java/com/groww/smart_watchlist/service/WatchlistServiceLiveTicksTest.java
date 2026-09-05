package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.WatchlistSummaryResponse;
import com.groww.smart_watchlist.exception.ResourceNotFoundException;
import com.groww.smart_watchlist.exception.UnauthorizedAccessException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * WatchlistService.subscribeToLiveTicks() ownership wiring — the actual
 * subscription bookkeeping is TickSimulationServiceTest's job; this only
 * checks the same ownership contract every other endpoint here enforces.
 */
@SpringBootTest
@Transactional
class WatchlistServiceLiveTicksTest {

    private static final Integer DEMO_USER_ID = 1;
    private static final Integer OTHER_USER_ID = 999;

    @Autowired
    private WatchlistService watchlistService;

    @Test
    void ownerCanSubscribeToTheirOwnWatchlistsLiveFeed() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Live Ticks Test - owner");

        SseEmitter emitter = watchlistService.subscribeToLiveTicks(watchlist.id(), DEMO_USER_ID);

        assertNotNull(emitter);
    }

    @Test
    void aDifferentUserCannotSubscribeToSomeoneElsesWatchlistLiveFeed() {
        WatchlistSummaryResponse watchlist =
                watchlistService.createWatchlist(DEMO_USER_ID, "Live Ticks Test - ownership");

        assertThrows(UnauthorizedAccessException.class,
                () -> watchlistService.subscribeToLiveTicks(watchlist.id(), OTHER_USER_ID));
    }

    @Test
    void subscribingToANonexistentWatchlistIs404() {
        assertThrows(ResourceNotFoundException.class,
                () -> watchlistService.subscribeToLiveTicks(999_999, DEMO_USER_ID));
    }
}
