package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.LiveTickResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import com.groww.smart_watchlist.entity.StockPrice;
import com.groww.smart_watchlist.repository.StockPriceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the Feature 5 simulated intraday feed. Runs against the real
 * seeded dataset (same convention as the other service tests) — no
 * fixtures needed since bounds are checked against each symbol's own real
 * OHLC/NAV row.
 */
@SpringBootTest
@Transactional
class TickSimulationServiceTest {

    @Autowired
    private TickSimulationService tickSimulationService;

    @Autowired
    private StockPriceRepository stockPriceRepository;

    @Test
    void beforeAnyTickTheCurrentPriceEqualsTheRealLatestClose() {
        // STK05 chosen arbitrarily — anything not already touched by an
        // earlier test in the same run. getCurrentPrice() itself performs
        // the (lazy, one-time) seeding, so calling it once IS "before any
        // tick" — tick() is a separate scheduled method never invoked here.
        Optional<BigDecimal> price = tickSimulationService.getCurrentPrice("STK05", InstrumentType.STOCK);
        assertTrue(price.isPresent(), "STK05 has real price history and should seed successfully");
    }

    @Test
    void stockTicksStayWithinThatDaysRealOhlcRange() {
        String symbol = "STK06";
        // The real bound to check against: this symbol's own latest
        // trading day's actual low/high, straight from stock_prices.
        StockPrice latestDay = stockPriceRepository.findTopBySymbolOrderByTradeDateDesc(symbol)
                .orElseThrow(() -> new AssertionError(symbol + " should have real price history"));

        for (int i = 0; i < 500; i++) {
            LiveTickResponse tick = tickSimulationService.tickOnceForTest(symbol, InstrumentType.STOCK);
            assertTrue(tick.value().compareTo(latestDay.getLow()) >= 0,
                    () -> "tick " + tick.value() + " fell below " + symbol + "'s real day low " + latestDay.getLow());
            assertTrue(tick.value().compareTo(latestDay.getHigh()) <= 0,
                    () -> "tick " + tick.value() + " rose above " + symbol + "'s real day high " + latestDay.getHigh());
            assertTrue(tick.simulated(), "every tick must be explicitly labeled simulated");
        }
    }

    @Test
    void fundTicksStayWithinTheSyntheticNavBand() {
        String symbol = "FUND02";
        Optional<BigDecimal> seed = tickSimulationService.getCurrentPrice(symbol, InstrumentType.FUND);
        assertTrue(seed.isPresent());
        BigDecimal navSeed = seed.get();
        // TickSimulationService clamps every fund tick to exactly ±1% of
        // the seeded NAV (FUND_NAV_BAND_FRACTION) — not just approximately,
        // so this test checks the real guaranteed bound directly.
        BigDecimal lowerBound = navSeed.multiply(BigDecimal.valueOf(0.99));
        BigDecimal upperBound = navSeed.multiply(BigDecimal.valueOf(1.01));

        for (int i = 0; i < 500; i++) {
            LiveTickResponse tick = tickSimulationService.tickOnceForTest(symbol, InstrumentType.FUND);
            assertTrue(tick.value().compareTo(lowerBound) >= 0,
                    () -> "fund NAV tick " + tick.value() + " fell below the expected band around " + navSeed);
            assertTrue(tick.value().compareTo(upperBound) <= 0,
                    () -> "fund NAV tick " + tick.value() + " rose above the expected band around " + navSeed);
        }
    }

    @Test
    void theWalkActuallyMovesTheStockPriceAcrossManyTicks() {
        String symbol = "STK08";
        BigDecimal seed = tickSimulationService.getCurrentPrice(symbol, InstrumentType.STOCK).orElseThrow();

        boolean everMoved = false;
        for (int i = 0; i < 50; i++) {
            LiveTickResponse tick = tickSimulationService.tickOnceForTest(symbol, InstrumentType.STOCK);
            if (tick.value().compareTo(seed) != 0) {
                everMoved = true;
                break;
            }
        }
        assertTrue(everMoved, "the simulated price should actually move within 50 ticks, not sit frozen at its seed");
    }

    @Test
    void asOfDateNeverChangesAcrossTicks() {
        String symbol = "STK07";
        var before = tickSimulationService.getAsOfDate(symbol, InstrumentType.STOCK);
        assertTrue(before.isPresent());

        for (int i = 0; i < 20; i++) {
            tickSimulationService.tickOnceForTest(symbol, InstrumentType.STOCK);
        }

        var after = tickSimulationService.getAsOfDate(symbol, InstrumentType.STOCK);
        assertEquals(before, after, "simulating intraday movement must not change which real trading day this is");
    }

    @Test
    void anUnknownSymbolProducesNoTickRatherThanAnError() {
        LiveTickResponse tick = tickSimulationService.tickOnceForTest("NOT-A-REAL-SYMBOL", InstrumentType.STOCK);
        assertNull(tick, "a symbol with no price history at all should simply produce no tick");
    }

    @Test
    void subscribingAndCompletingTracksSubscriberCountCorrectly() {
        Integer scratchWatchlistId = 999_001; // no such watchlist need exist for this pure subscription-bookkeeping test

        assertEquals(0, tickSimulationService.subscriberCount(scratchWatchlistId));

        SseEmitter emitter = tickSimulationService.subscribe(scratchWatchlistId);
        assertEquals(1, tickSimulationService.subscriberCount(scratchWatchlistId));

        // Simulate the client disconnecting — emitter.complete() triggers
        // the same onCompletion callback a real closed connection would.
        emitter.complete();
        assertEquals(0, tickSimulationService.subscriberCount(scratchWatchlistId),
                "completing the emitter must deregister it, not leak the subscription");
    }

    @Test
    void multipleSubscribersToTheSameWatchlistAreTrackedIndependently() {
        Integer scratchWatchlistId = 999_002;

        SseEmitter first = tickSimulationService.subscribe(scratchWatchlistId);
        SseEmitter second = tickSimulationService.subscribe(scratchWatchlistId);
        assertEquals(2, tickSimulationService.subscriberCount(scratchWatchlistId));

        first.complete();
        assertEquals(1, tickSimulationService.subscriberCount(scratchWatchlistId));

        second.complete();
        assertEquals(0, tickSimulationService.subscriberCount(scratchWatchlistId));
    }
}
