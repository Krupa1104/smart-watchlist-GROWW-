package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.InstrumentSummaryResponse;
import com.groww.smart_watchlist.dto.MarketDataResponse;
import com.groww.smart_watchlist.entity.Fund;
import com.groww.smart_watchlist.entity.InstrumentType;
import com.groww.smart_watchlist.entity.Stock;
import com.groww.smart_watchlist.exception.ResourceNotFoundException;
import com.groww.smart_watchlist.repository.FundNavRepository;
import com.groww.smart_watchlist.repository.FundRepository;
import com.groww.smart_watchlist.repository.StockPriceRepository;
import com.groww.smart_watchlist.repository.StockRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

// Deliberately the only place in the app that knows "STOCK reads from
// stock_prices, FUND reads from fund_navs". WatchlistService just asks this
// for "current data for this symbol+type" and gets back one uniform shape —
// keeps the STOCK/FUND branching in exactly one spot instead of leaking into
// every caller, which matters once Phase 3 (detection) needs the same data.
@Service
public class MarketDataService {

    private final StockRepository stockRepository;
    private final FundRepository fundRepository;
    private final StockPriceRepository stockPriceRepository;
    private final FundNavRepository fundNavRepository;

    public MarketDataService(StockRepository stockRepository,
                              FundRepository fundRepository,
                              StockPriceRepository stockPriceRepository,
                              FundNavRepository fundNavRepository) {
        this.stockRepository = stockRepository;
        this.fundRepository = fundRepository;
        this.stockPriceRepository = stockPriceRepository;
        this.fundNavRepository = fundNavRepository;
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

    private MarketDataResponse getLatestStockData(String symbol) {
        Stock stock = stockRepository.findById(symbol)
                .orElseThrow(() -> new ResourceNotFoundException("Stock not found: " + symbol));

        return stockPriceRepository.findTopBySymbolOrderByTradeDateDesc(symbol)
                .map(price -> new MarketDataResponse(
                        symbol,
                        InstrumentType.STOCK,
                        stock.getName(),
                        stock.getSector(),
                        price.getClose(),
                        price.getTradeDate(),
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

        return fundNavRepository.findTopBySymbolOrderByNavDateDesc(symbol)
                .map(nav -> new MarketDataResponse(
                        symbol,
                        InstrumentType.FUND,
                        fund.getName(),
                        fund.getCategory(),
                        nav.getNav(),
                        nav.getNavDate(),
                        true
                ))
                .orElseGet(() -> new MarketDataResponse(
                        symbol, InstrumentType.FUND, fund.getName(), fund.getCategory(),
                        null, null, false
                ));
    }
}
