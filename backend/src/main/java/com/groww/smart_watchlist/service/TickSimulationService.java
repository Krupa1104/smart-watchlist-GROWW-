package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.LiveTickResponse;
import com.groww.smart_watchlist.entity.FundNav;
import com.groww.smart_watchlist.entity.InstrumentType;
import com.groww.smart_watchlist.entity.StockPrice;
import com.groww.smart_watchlist.entity.WatchlistItem;
import com.groww.smart_watchlist.repository.FundNavRepository;
import com.groww.smart_watchlist.repository.StockPriceRepository;
import com.groww.smart_watchlist.repository.WatchlistItemRepository;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

// DEMO simulated intraday feed — NOT real market data (see class-level and
// frontend labeling requirements). Generates bounded, in-memory-only price
// movement from the existing static daily OHLC/NAV dataset; never writes a
// tick to PostgreSQL, never touches events.json/stocks.json/etc, and never
// regenerates or replaces the historical daily rows ChangeDetectionService
// reads — that service queries stock_prices/fund_navs directly and has no
// dependency on this class, so anomaly/1D-change detection is completely
// unaffected by anything here. This class is deliberately the ONLY place
// that knows a "current price" can differ from the day's real close.
//
// Lazy, on-demand simulation: a symbol only starts ticking once something
// actually asks for it (a REST call via MarketDataService, or an SSE
// subscription for a watchlist containing it) — idle symbols on no one's
// watchlist are never simulated, keeping this cheap regardless of dataset size.
@Service
public class TickSimulationService {

    // Stocks: bounded to a fraction of the day's own (high-low) range per
    // tick — realistic relative to that day's actual volatility, never an
    // arbitrary/unbounded jump. Funds have no OHLC range (fund_navs is a
    // single daily NAV, not OHLC), so they get a narrow synthetic band
    // around the latest NAV instead, consistent with this codebase's own
    // existing assumption (see ChangeDetectionService) that funds move
    // slower/smoother than stocks.
    private static final BigDecimal STOCK_MAX_STEP_FRACTION_OF_RANGE = BigDecimal.valueOf(0.05);
    private static final BigDecimal FUND_NAV_BAND_FRACTION = BigDecimal.valueOf(0.01); // ±1% band around latest NAV
    private static final BigDecimal FUND_MAX_STEP_FRACTION_OF_BAND = BigDecimal.valueOf(0.08);

    // Fixed seed: bounded random movement that's reproducible across runs
    // rather than producing a different, possibly-wilder sequence every
    // time the backend restarts — easier to reason about and to test.
    private final Random random = new Random(42);

    private final StockPriceRepository stockPriceRepository;
    private final FundNavRepository fundNavRepository;
    private final WatchlistItemRepository watchlistItemRepository;

    private final ConcurrentHashMap<String, TickState> stockStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TickState> fundStates = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, CopyOnWriteArrayList<SseEmitter>> subscribersByWatchlist =
            new ConcurrentHashMap<>();

    public TickSimulationService(StockPriceRepository stockPriceRepository,
                                  FundNavRepository fundNavRepository,
                                  WatchlistItemRepository watchlistItemRepository) {
        this.stockPriceRepository = stockPriceRepository;
        this.fundNavRepository = fundNavRepository;
        this.watchlistItemRepository = watchlistItemRepository;
    }

    // ---------- read side: used by MarketDataService for every REST response ----------

    /**
     * The current simulated price for this symbol, lazily seeded from its
     * real latest daily close/NAV the first time anything asks for it. On
     * that very first call — before any tick has run — this equals the
     * real close/NAV exactly, so nothing looks different until the
     * scheduled simulation has actually had a chance to move it.
     */
    public Optional<BigDecimal> getCurrentPrice(String symbol, InstrumentType instrumentType) {
        TickState state = stateFor(symbol, instrumentType);
        return state == null ? Optional.empty() : Optional.of(state.current.get());
    }

    /** The real trading day this simulation is bounded within — unchanged by ticking. */
    public Optional<LocalDate> getAsOfDate(String symbol, InstrumentType instrumentType) {
        TickState state = stateFor(symbol, instrumentType);
        return state == null ? Optional.empty() : Optional.of(state.asOfDate);
    }

    private TickState stateFor(String symbol, InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.STOCK) {
            return stockStates.computeIfAbsent(symbol, this::seedStockState);
        }
        return fundStates.computeIfAbsent(symbol, this::seedFundState);
    }

    private TickState seedStockState(String symbol) {
        return stockPriceRepository.findTopBySymbolOrderByTradeDateDesc(symbol)
                .map(this::toStockTickState)
                .orElse(null); // no price history at all yet — nothing to simulate, MarketDataService's existing "no data" path applies
    }

    private TickState toStockTickState(StockPrice p) {
        return new TickState(p.getLow(), p.getHigh(), p.getClose(), p.getTradeDate());
    }

    private TickState seedFundState(String symbol) {
        return fundNavRepository.findTopBySymbolOrderByNavDateDesc(symbol)
                .map(this::toFundTickState)
                .orElse(null);
    }

    private TickState toFundTickState(FundNav n) {
        BigDecimal nav = n.getNav();
        BigDecimal band = nav.multiply(FUND_NAV_BAND_FRACTION);
        return new TickState(nav.subtract(band), nav.add(band), nav, n.getNavDate());
    }

    // ---------- SSE subscription side ----------

    /**
     * Registers a new SSE subscriber for a watchlist. No timeout (0L) —
     * the client (browser tab) controls the connection's lifetime.
     *
     * Cleanup has two paths, both wired here:
     *  1. A REAL client disconnect that Spring's own async request
     *     machinery detects (browser tab closed, connection dropped)
     *     invokes the onCompletion/onTimeout/onError callbacks below —
     *     this is the correct, necessary mechanism for that case and
     *     requires no explicit complete() call from anyone.
     *  2. Something in OUR OWN code (or a test) calling complete()/
     *     completeWithError() directly on the returned emitter. Plain
     *     SseEmitter only runs its completion callbacks through the
     *     servlet-async completion listener installed during
     *     initialize() — which never happens for an emitter that was
     *     never attached to a live request (exactly the case when a
     *     test calls emitter.complete() directly on a freshly-created
     *     emitter). TrackedEmitter closes that gap by deregistering
     *     synchronously as part of complete()/completeWithError()
     *     themselves, so cleanup is deterministic regardless of whether
     *     a live request context is present.
     * unsubscribe() is idempotent, so it's harmless for both paths to
     * end up calling it for the same emitter.
     */
    public SseEmitter subscribe(Integer watchlistId) {
        TrackedEmitter emitter = new TrackedEmitter(watchlistId);
        subscribersByWatchlist.computeIfAbsent(watchlistId, id -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> unsubscribe(watchlistId, emitter));
        emitter.onTimeout(() -> unsubscribe(watchlistId, emitter));
        emitter.onError(ex -> unsubscribe(watchlistId, emitter));

        return emitter;
    }

    private void unsubscribe(Integer watchlistId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = subscribersByWatchlist.get(watchlistId);
        if (list == null) return;
        list.remove(emitter);
        if (list.isEmpty()) {
            subscribersByWatchlist.remove(watchlistId, list);
        }
    }

    /**
     * A plain SseEmitter whose complete()/completeWithError() ALSO
     * deregister it from subscribersByWatchlist synchronously, closing the
     * gap where those callbacks would otherwise only fire via Spring's
     * async-request completion listener (see subscribe()'s javadoc).
     * Deliberately doesn't touch send()/anything else — same emitter
     * behavior and the same SSE wire contract in every other respect.
     */
    private final class TrackedEmitter extends SseEmitter {
        private final Integer watchlistId;

        TrackedEmitter(Integer watchlistId) {
            super(0L);
            this.watchlistId = watchlistId;
        }

        @Override
        public void complete() {
            try {
                super.complete();
            } finally {
                unsubscribe(watchlistId, this);
            }
        }

        @Override
        public void completeWithError(Throwable ex) {
            try {
                super.completeWithError(ex);
            } finally {
                unsubscribe(watchlistId, this);
            }
        }
    }

    /** Test/diagnostic hook — not used by production code paths. */
    int subscriberCount(Integer watchlistId) {
        CopyOnWriteArrayList<SseEmitter> list = subscribersByWatchlist.get(watchlistId);
        return list == null ? 0 : list.size();
    }

    // ---------- the simulation clock ----------

    // ~1 simulated market minute per 1 real second, as specified — one
    // batched "tick" event per watchlist per second, not one event per
    // instrument, so a 12-item watchlist doesn't mean 12x the SSE traffic
    // or 12x the frontend re-renders per second.
    @Scheduled(fixedRate = 1000)
    void tick() {
        if (subscribersByWatchlist.isEmpty()) {
            return; // nobody listening — advancing prices no one sees would just be wasted CPU
        }
        subscribersByWatchlist.forEach(this::tickOneWatchlist);
    }

    private void tickOneWatchlist(Integer watchlistId, CopyOnWriteArrayList<SseEmitter> emitters) {
        if (emitters.isEmpty()) {
            return;
        }
        List<WatchlistItem> items = watchlistItemRepository.findByWatchlistId(watchlistId);
        List<LiveTickResponse> batch = new ArrayList<>(items.size());
        for (WatchlistItem item : items) {
            LiveTickResponse tick = advanceAndBuildTick(item.getSymbol(), item.getInstrumentType());
            if (tick != null) {
                batch.add(tick);
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("tick").data(batch, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException ex) {
                // client went away between our liveness check and this send —
                // drop them the same way a clean disconnect would
                unsubscribe(watchlistId, emitter);
                emitter.completeWithError(ex);
            }
        }
    }

    private LiveTickResponse advanceAndBuildTick(String symbol, InstrumentType instrumentType) {
        TickState state = stateFor(symbol, instrumentType);
        if (state == null) {
            return null; // symbol has no price history at all — nothing to simulate
        }
        BigDecimal maxStep = instrumentType == InstrumentType.STOCK
                ? state.range().multiply(STOCK_MAX_STEP_FRACTION_OF_RANGE)
                : state.range().multiply(FUND_MAX_STEP_FRACTION_OF_BAND);

        double randomUnit = (random.nextDouble() * 2) - 1; // uniform in [-1, 1)
        BigDecimal step = maxStep.multiply(BigDecimal.valueOf(randomUnit));

        state.current.updateAndGet(current -> clamp(
                current.add(step).setScale(4, RoundingMode.HALF_UP), state.low, state.high));

        return new LiveTickResponse(symbol, instrumentType, state.current.get(), state.asOfDate, true);
    }

    private BigDecimal clamp(BigDecimal value, BigDecimal low, BigDecimal high) {
        if (value.compareTo(low) < 0) return low;
        if (value.compareTo(high) > 0) return high;
        return value;
    }

    /** Package-private for tests only — lets tests advance a symbol on demand without waiting a real second. */
    LiveTickResponse tickOnceForTest(String symbol, InstrumentType instrumentType) {
        return advanceAndBuildTick(symbol, instrumentType);
    }

    private static final class TickState {
        final BigDecimal low;
        final BigDecimal high;
        final LocalDate asOfDate;
        final AtomicReference<BigDecimal> current;

        TickState(BigDecimal low, BigDecimal high, BigDecimal current, LocalDate asOfDate) {
            this.low = low;
            this.high = high;
            this.asOfDate = asOfDate;
            this.current = new AtomicReference<>(current);
        }

        BigDecimal range() {
            BigDecimal r = high.subtract(low);
            // A flat/zero range (e.g. a fund's synthetic band collapsing at
            // NAV=0, or a stock's low==high day) would make every step 0 —
            // harmless, just means this symbol won't visibly move, not a
            // divide-by-zero or similar failure.
            return r;
        }
    }
}
