package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.InstrumentSummaryResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for MarketDataService.listAllInstruments(), which backs the new
 * GET /api/instruments endpoint (see InstrumentController) added for the
 * frontend's global instrument search. Runs against the real seeded dataset
 * (36 stocks + 15 funds, per data-generator/README.md) — no fixtures needed
 * since this is read-only reference data. @Transactional purely for
 * consistency with the other service tests; this method never writes
 * anything.
 */
@SpringBootTest
@Transactional
class MarketDataInstrumentListingTest {

    @Autowired
    private MarketDataService marketDataService;

    @Test
    void returnsEveryStockAndFundInTheSeededDataset() {
        List<InstrumentSummaryResponse> instruments = marketDataService.listAllInstruments();

        assertEquals(51, instruments.size(),
                "expected 36 stocks + 15 funds = 51 instruments total; got " + instruments.size());

        long stockCount = instruments.stream().filter(i -> i.instrumentType() == InstrumentType.STOCK).count();
        long fundCount = instruments.stream().filter(i -> i.instrumentType() == InstrumentType.FUND).count();
        assertEquals(36, stockCount, "expected 36 stocks");
        assertEquals(15, fundCount, "expected 15 funds");
    }

    @Test
    void everyInstrumentHasASymbolNameAndGroupLabel() {
        List<InstrumentSummaryResponse> instruments = marketDataService.listAllInstruments();

        assertTrue(instruments.stream().allMatch(i ->
                        i.symbol() != null && !i.symbol().isBlank()
                                && i.name() != null && !i.name().isBlank()
                                && i.groupLabel() != null && !i.groupLabel().isBlank()),
                "every instrument should carry a symbol, display name, and group label (sector/category)");
    }

    @Test
    void aKnownSeededStockAndFundAreBothPresentWithCorrectShape() {
        List<InstrumentSummaryResponse> instruments = marketDataService.listAllInstruments();

        InstrumentSummaryResponse stk01 = instruments.stream()
                .filter(i -> i.symbol().equals("STK01"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("STK01 should be present"));
        assertEquals(InstrumentType.STOCK, stk01.instrumentType());

        InstrumentSummaryResponse fund01 = instruments.stream()
                .filter(i -> i.symbol().equals("FUND01"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("FUND01 should be present"));
        assertEquals(InstrumentType.FUND, fund01.instrumentType());
    }

    @Test
    void resultsAreSortedBySymbol() {
        List<InstrumentSummaryResponse> instruments = marketDataService.listAllInstruments();

        List<String> symbols = instruments.stream().map(InstrumentSummaryResponse::symbol).toList();
        List<String> sorted = symbols.stream().sorted(Comparator.naturalOrder()).toList();
        assertEquals(sorted, symbols, "instruments should be sorted by symbol for stable, predictable search results");
    }
}
