package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.InstrumentSummaryResponse;
import com.groww.smart_watchlist.dto.MarketDataResponse;
import com.groww.smart_watchlist.dto.PricePointResponse;
import com.groww.smart_watchlist.entity.Fund;
import com.groww.smart_watchlist.entity.FundNav;
import com.groww.smart_watchlist.entity.InstrumentType;
import com.groww.smart_watchlist.entity.Stock;
import com.groww.smart_watchlist.entity.StockPrice;
import com.groww.smart_watchlist.exception.ResourceNotFoundException;
import com.groww.smart_watchlist.repository.FundNavRepository;
import com.groww.smart_watchlist.repository.FundRepository;
import com.groww.smart_watchlist.repository.StockPriceRepository;
import com.groww.smart_watchlist.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.math.BigDecimal;

// Deliberately the only place in the app that knows "STOCK reads from
// stock_prices, FUND reads from fund_navs". WatchlistService just asks this
// for "current data for this symbol+type" and gets back one uniform shape —
// keeps the STOCK/FUND branching in exactly one spot instead of leaking into
// every caller, which matters once Phase 3 (detection) needs the same data.
@Service
public class MarketDataService {

    // Recent-history depth for the instrument detail panel's mini chart —
    // enough to see a real trend/shape without shipping the full ~126-day
    // series for a simple sparkline.
    private static final int RECENT_HISTORY_LIMIT = 30;

    private final StockRepository stockRepository;
    private final FundRepository fundRepository;
    private final StockPriceRepository stockPriceRepository;
    private final FundNavRepository fundNavRepository;
    private final TickSimulationService tickSimulationService;

    public MarketDataService(StockRepository stockRepository,
                              FundRepository fundRepository,
                              StockPriceRepository stockPriceRepository,
                              FundNavRepository fundNavRepository,
                              TickSimulationService tickSimulationService) {
        this.stockRepository = stockRepository;
        this.fundRepository = fundRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.fundNavRepository = fundNavRepository;
        this.tickSimulationService = tickSimulationService;
    }

    /**
     * Confirms a symbol actually exists as the given instrument type.
     * Used before adding it to a watchlist — cheap validation against
     * junk symbols, distinct from whether price/NAV history exists yet.
     */
    public boolean symbolExists(String symbol, InstrumentType instrumentType) {
        return instrumentType == InstrumentType.STOCK
                ? stockRepository.existsById(symbol)
                : fundRepository.existsById(symbol);
    }

    /**
     * Reference data for every stock and fund that exists — not just what's
     * on any particular watchlist. Backs the frontend's global instrument
     * search (add-item autocomplete), which fetches this once and filters
     * it client-side rather than hitting the backend on every keystroke.
     * Deliberately no price/NAV lookup per row here — that N+1 would only
     * be justified if callers needed current values, and this one doesn't.
     */
    public List<InstrumentSummaryResponse> listAllInstruments() {
        List<InstrumentSummaryResponse> all = new ArrayList<>();
        stockRepository.findAll().forEach(s ->
                all.add(new InstrumentSummaryResponse(s.getSymbol(), InstrumentType.STOCK, s.getName(), s.getSector())));
        fundRepository.findAll().forEach(f ->
                all.add(new InstrumentSummaryResponse(f.getSymbol(), InstrumentType.FUND, f.getName(), f.getCategory())));
        return all.stream()
                .sorted(Comparator.comparing(InstrumentSummaryResponse::symbol))
                .toList();
    }

    public MarketDataResponse getLatestMarketData(String symbol, InstrumentType instrumentType) {
        return instrumentType == InstrumentType.STOCK
                ? getLatestStockData(symbol)
                : getLatestFundData(symbol);
    }

    /**
     * Recent daily history for the instrument detail panel — oldest first,
     * so the frontend can plot it left-to-right without re-sorting. Reuses
     * the existing findBySymbolOrderBy*Desc queries (no new SQL, no schema
     * change); this is presentation-only, distinct from the rolling
     * statistics ChangeDetectionService computes over the same tables.
     */
    public List<PricePointResponse> getRecentHistory(String symbol, InstrumentType instrumentType) {
        if (instrumentType == InstrumentType.STOCK) {
            List<StockPrice> rows = stockPriceRepository.findBySymbolOrderByTradeDateDesc(symbol);
            return rows.stream()
                    .limit(RECENT_HISTORY_LIMIT)
                    .sorted(Comparator.comparing(StockPrice::getTradeDate))
                    .map(p -> new PricePointResponse(p.getTradeDate(), p.getClose(), p.getVolume()))
                    .toList();
        }
        List<FundNav> rows = fundNavRepository.findBySymbolOrderByNavDateDesc(symbol);
        return rows.stream()
                .limit(RECENT_HISTORY_LIMIT)
                .sorted(Comparator.comparing(FundNav::getNavDate))
                .map(n -> new PricePointResponse(n.getNavDate(), n.getNav(), null))
                .toList();
    }

    // Feature 5: `latestValue` here is now the SIMULATED current price (see
    // TickSimulationService), not always the raw DB close. This is the one
    // seam that makes checkWatchlist()/the watchlist table/the detail panel
    // all reflect the simulated feed automatically, with zero changes to
    // SnapshotService, WatchlistService's check/detect flow, or
    // ChangeDetectionService (which reads stock_prices/fund_navs directly
    // and never calls this method) — anomaly detection and "1D change"
    // stay pinned to the real historical daily close, exactly as before.
    // On the very first call for a symbol, before any tick has run, this
    // equals the real close/NAV exactly (see TickSimulationService's
    // seeding) — nothing looks different until simulation has actually
    // had a chance to move it.
    private MarketDataResponse getLatestStockData(String symbol) {
        Stock stock = stockRepository.findById(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + symbol));

        Optional<BigDecimal> simulatedPrice = tickSimulationService.getCurrentPrice(symbol, InstrumentType.STOCK);
        return simulatedPrice
                .map(price -> new MarketDataResponse(
                        symbol,
                        InstrumentType.STOCK,
                        stock.getName(),
                        stock.getSector(),
                        price,
                        tickSimulationService.getAsOfDate(symbol, InstrumentType.STOCK).orElse(null),
                        true
                ))
                // Symbol is real but has no price rows yet — surface that
                // explicitly rather than a silent null the frontend has to guess about.
                .orElseGet(() -> new MarketDataResponse(
                        symbol, InstrumentType.STOCK, stock.getName(), stock.getSector(),
                        null, null, false
                ));
    }

    private MarketDataResponse getLatestFundData(String symbol) {
        Fund fund = fundRepository.findById(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Fund not found: " + symbol));

        Optional<BigDecimal> simulatedPrice = tickSimulationService.getCurrentPrice(symbol, InstrumentType.FUND);
        return simulatedPrice
                .map(price -> new MarketDataResponse(
                        symbol,
                        InstrumentType.FUND,
                        fund.getName(),
                        fund.getCategory(),
                        price,
                        tickSimulationService.getAsOfDate(symbol, InstrumentType.FUND).orElse(null),
                        true
                ))
                .orElseGet(() -> new MarketDataResponse(
                        symbol, InstrumentType.FUND, fund.getName(), fund.getCategory(),
                        null, null, false
                ));
    }
}
