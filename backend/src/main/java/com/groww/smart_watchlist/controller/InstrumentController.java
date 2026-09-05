package com.groww.smart_watchlist.controller;

import com.groww.smart_watchlist.dto.InstrumentSummaryResponse;
import com.groww.smart_watchlist.service.MarketDataService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// Deliberately separate from WatchlistController: this is reference data
// ("what instruments exist"), not something scoped to a user or a
// watchlist, so it doesn't take a userId param like every endpoint over
// there does. Added specifically to back the frontend's global instrument
// search (see AppHeader.jsx) — the frontend fetches this once and filters
// it locally rather than calling this per keystroke.
@RestController
@RequestMapping("/api/instruments")
public class InstrumentController {

    private final MarketDataService marketDataService;

    public InstrumentController(MarketDataService marketDataService) {
        this.marketDataService = marketDataService;
    }

    @GetMapping
    public List<InstrumentSummaryResponse> listInstruments() {
        return marketDataService.listAllInstruments();
    }
}
