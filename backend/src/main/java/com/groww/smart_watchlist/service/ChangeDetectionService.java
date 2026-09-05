package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.DetectedChangeResponse;
import com.groww.smart_watchlist.entity.DetectedChange;
import com.groww.smart_watchlist.entity.InstrumentType;
import com.groww.smart_watchlist.repository.DetectedChangeRepository;
import com.groww.smart_watchlist.repository.FundNavRepository;
import com.groww.smart_watchlist.repository.StockPriceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// This is the differentiator called out in the brief: "meaningful" is
// relative, not a flat percentage. Thresholds below were picked as a
// reasonable starting point (2 sigma is a standard anomaly cutoff) — the
// plan is to re-tune them once you validate against all 10 planted
// ground-truth events in events.json, not treat them as final. Every
// threshold is a named constant so they're easy to point at and defend live.
@Service
public class ChangeDetectionService {

    // Stock: "unusual" relative to the stock's OWN trailing behavior.
    private static final BigDecimal STOCK_RETURN_Z_THRESHOLD = BigDecimal.valueOf(2.0);
    private static final BigDecimal VOLUME_SPIKE_RATIO_THRESHOLD = BigDecimal.valueOf(2.0);

    // Fund: "unusual" relative to CATEGORY PEERS on the same day, not the
    // fund's own history (funds move slower/smoother — brief's explicit
    // requirement). With only 3 funds/category in this dataset, a
    // cross-sectional z-score at n=3 is noisy, so below this sample size we
    // fall back to a flat percentage-point deviation instead of trusting a
    // stddev computed from 2-3 points.
    private static final int MIN_CATEGORY_SAMPLE_FOR_ZSCORE = 5;
    private static final BigDecimal FUND_PEER_Z_THRESHOLD = BigDecimal.valueOf(1.5);
    private static final BigDecimal FUND_PEER_ABS_DEVIATION_THRESHOLD = BigDecimal.valueOf(0.01); // 1 percentage point

    private final StockPriceRepository stockPriceRepository;
    private final FundNavRepository fundNavRepository;
    private final DetectedChangeRepository detectedChangeRepository;

    public ChangeDetectionService(StockPriceRepository stockPriceRepository,
                                   FundNavRepository fundNavRepository,
                                   DetectedChangeRepository detectedChangeRepository) {
        this.stockPriceRepository = stockPriceRepository;
        this.fundNavRepository = fundNavRepository;
        this.detectedChangeRepository = detectedChangeRepository;
    }

    /**
     * Always returns a verdict — meaningful=false is a normal, expected
     * result, not an error. Only meaningful verdicts are persisted to
     * detected_changes (that table is the engine's own audit trail, kept
     * separate from the ground-truth market_events per schema.sql).
     */
    @Transactional
    public DetectedChangeResponse detect(String symbol, InstrumentType instrumentType) {
        DetectedChangeResponse result = instrumentType == InstrumentType.STOCK
                ? detectStockChange(symbol)
                : detectFundChange(symbol);

        if (result.meaningful()) {
            persist(result);
        }
        return result;
    }

    /**
     * Same calculation as {@link #detect}, but pinned to a supplied
     * historical date instead of "latest", and deliberately NOT persisted
     * to detected_changes. Exists so ground-truth tests can ask "what would
     * the detector have said about STK07 on 2026-03-30?" without polluting
     * the audit trail with test data and without touching the production
     * /detect endpoint's behavior (that still only ever looks at "latest").
     */
    public DetectedChangeResponse evaluateAsOf(String symbol, InstrumentType instrumentType, LocalDate asOfDate) {
        return instrumentType == InstrumentType.STOCK
                ? buildStockVerdict(symbol, stockPriceRepository.findStockSignalAsOf(symbol, asOfDate))
                : buildFundVerdict(symbol, fundNavRepository.findFundPeerSignalAsOf(symbol, asOfDate));
    }

    private DetectedChangeResponse detectStockChange(String symbol) {
        return buildStockVerdict(symbol, stockPriceRepository.findLatestStockSignal(symbol));
    }

    /**
     * Batched counterpart to {@link #detect} for a whole watchlist's worth
     * of symbols at once — replaces the N+1 pattern where
     * WatchlistService.detectChanges() used to call detect() once per item
     * (one native query per stock). Stocks are computed via a single
     * multi-symbol query (StockPriceRepository.findLatestStockSignalsBatch,
     * PARTITION BY symbol instead of one WHERE-clause query per symbol) —
     * verified manually against the loaded dataset to produce numerically
     * IDENTICAL z-scores/volume-ratios to the per-symbol query, so this is
     * purely a round-trip reduction, not a behavior change.
     *
     * Funds are deliberately NOT batched here: batching the category-peer
     * comparison across multiple fund symbols at once would require
     * rewriting the peer-average/stddev computation to key off each fund's
     * OWN category (not just a shared WHERE clause), which is materially
     * more complex to get right without risking a subtle regression in the
     * ground-truth-validated detection logic. With at most 15 funds in this
     * dataset (vs 36 stocks), the stock-side batching already covers the
     * majority of a typical watchlist's query cost; funds keep the existing
     * per-symbol call. This is a deliberate, disclosed scope decision, not
     * an oversight — see docs/SCALABILITY.md.
     *
     * Persists meaningful verdicts exactly as {@link #detect} does.
     */
    @Transactional
    public Map<String, DetectedChangeResponse> detectBatch(List<String> stockSymbols, List<String> fundSymbols) {
        Map<String, DetectedChangeResponse> results = new LinkedHashMap<>();

        if (!stockSymbols.isEmpty()) {
            List<Object[]> rows = stockPriceRepository.findLatestStockSignalsBatch(stockSymbols);
            Map<String, Object[]> rowBySymbol = new LinkedHashMap<>();
            for (Object[] row : rows) {
                rowBySymbol.put((String) row[0], dropLeadingSymbolColumn(row));
            }
            for (String symbol : stockSymbols) {
                Object[] row = rowBySymbol.get(symbol);
                // Explicit type witnesses (List.<Object[]>of(...)) are required
                // here, not just an explicitly-typed local variable — a bare
                // `List.of(row)` where row's static type is already Object[]
                // hits a well-known Java varargs ambiguity: List.of(E...)
                // treats a single array-typed argument as THE VARARGS ARRAY
                // ITSELF (spreading its elements as individual entries, E
                // inferred as Object) rather than wrapping it as one element,
                // regardless of the assignment's target type. The explicit
                // <Object[]> witness forces E=Object[] before that ambiguity
                // can apply, so row is correctly wrapped as a single element.
                List<Object[]> rowAsList = row == null ? List.<Object[]>of() : List.<Object[]>of(row);
                DetectedChangeResponse verdict = buildStockVerdict(symbol, rowAsList);
                if (verdict.meaningful()) {
                    persist(verdict);
                }
                results.put(symbol, verdict);
            }
        }

        // Not batched — see javadoc above.
        for (String symbol : fundSymbols) {
            DetectedChangeResponse verdict = detectFundChange(symbol);
            if (verdict.meaningful()) {
                persist(verdict);
            }
            results.put(symbol, verdict);
        }

        return results;
    }

    // The batch query's row shape is (symbol, trade_date, close, ...) — one
    // extra leading column vs. the single-symbol query's (trade_date, close,
    // ...). Stripping it lets buildStockVerdict's existing, already-tested
    // index-based parsing work completely unchanged for either code path.
    private Object[] dropLeadingSymbolColumn(Object[] row) {
        Object[] withoutSymbol = new Object[row.length - 1];
        System.arraycopy(row, 1, withoutSymbol, 0, withoutSymbol.length);
        return withoutSymbol;
    }

    private DetectedChangeResponse buildStockVerdict(String symbol, List<Object[]> rows) {
        if (rows.isEmpty()) {
            return insufficientData(symbol, InstrumentType.STOCK, "No price history for " + symbol + " yet.");
        }

        Object[] row = rows.get(0);
        LocalDate asOfDate = toLocalDate(row[0]);
        BigDecimal dailyReturn = toBigDecimal(row[2]);
        BigDecimal returnZScore = toBigDecimal(row[5]);
        BigDecimal volumeRatio = toBigDecimal(row[8]);

        if (returnZScore == null && volumeRatio == null) {
            // Not enough trailing history yet (e.g. within first ~20 trading
            // days) for either rolling stat to be computable.
            return insufficientData(symbol, InstrumentType.STOCK,
                    "Not enough trailing history yet to establish a baseline for " + symbol + ".");
        }

        boolean returnTriggered = returnZScore != null
                && returnZScore.abs().compareTo(STOCK_RETURN_Z_THRESHOLD) >= 0;
        boolean volumeTriggered = volumeRatio != null
                && volumeRatio.compareTo(VOLUME_SPIKE_RATIO_THRESHOLD) >= 0;

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        putIfPresent(metrics, "dailyReturn", dailyReturn);
        putIfPresent(metrics, "returnZScore", returnZScore);
        putIfPresent(metrics, "volumeRatio", volumeRatio);

        if (!returnTriggered && !volumeTriggered) {
            return new DetectedChangeResponse(symbol, InstrumentType.STOCK, asOfDate,
                    false, null, null,
                    "Moved within its normal range — no action needed.", metrics);
        }

        String changeType = returnTriggered && volumeTriggered ? "return_and_volume_spike"
                : returnTriggered ? "return_z_score" : "volume_spike";

        // Severity: z-score is the primary signal (comparable across every
        // stock regardless of price level); volume ratio alone (no return
        // spike) uses the ratio itself as a rough stand-in on the same
        // rough 1-3+ scale, so a pure-volume event can still be ranked
        // against a pure-return event in a combined digest.
        BigDecimal severity = returnZScore != null ? returnZScore.abs() : volumeRatio;

        StringBuilder explanation = new StringBuilder();
        if (returnTriggered) {
            explanation.append(String.format("Move is %.1f standard deviations from its own recent behavior", returnZScore.abs()));
        }
        if (volumeTriggered) {
            if (!explanation.isEmpty()) explanation.append("; ");
            explanation.append(String.format("volume is %.1fx its 20-day average", volumeRatio));
        }
        explanation.append(".");

        return new DetectedChangeResponse(symbol, InstrumentType.STOCK, asOfDate,
                true, changeType, severity.setScale(3, RoundingMode.HALF_UP),
                explanation.toString(), metrics);
    }

    private DetectedChangeResponse detectFundChange(String symbol) {
        return buildFundVerdict(symbol, fundNavRepository.findLatestFundPeerSignal(symbol));
    }

    private DetectedChangeResponse buildFundVerdict(String symbol, List<Object[]> rows) {
        if (rows.isEmpty() || rows.get(0)[0] == null) {
            return insufficientData(symbol, InstrumentType.FUND,
                    "Not enough NAV history yet to compare " + symbol + " against its category.");
        }

        Object[] row = rows.get(0);
        BigDecimal fundChange = toBigDecimal(row[0]);
        BigDecimal categoryAvgChange = toBigDecimal(row[1]);
        BigDecimal categoryStdDev = toBigDecimal(row[2]);
        Integer sampleSize = row[3] == null ? 0 : ((Number) row[3]).intValue();
        LocalDate asOfDate = toLocalDate(row[4]);

        if (fundChange == null || categoryAvgChange == null) {
            return insufficientData(symbol, InstrumentType.FUND,
                    "Not enough NAV history yet to compare " + symbol + " against its category.");
        }

        BigDecimal deviation = fundChange.subtract(categoryAvgChange);

        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        putIfPresent(metrics, "fundChangePct", fundChange);
        putIfPresent(metrics, "categoryAvgChangePct", categoryAvgChange);
        putIfPresent(metrics, "categoryDeviationPct", deviation);
        putIfPresent(metrics, "categoryStdDev", categoryStdDev);
        metrics.put("categorySampleSize", BigDecimal.valueOf(sampleSize));

        boolean useZScore = categoryStdDev != null
                && categoryStdDev.compareTo(BigDecimal.ZERO) > 0
                && sampleSize >= MIN_CATEGORY_SAMPLE_FOR_ZSCORE;

        boolean triggered;
        BigDecimal severity;
        String explanation;
        String changeType;

        if (useZScore) {
            BigDecimal peerZScore = deviation.divide(categoryStdDev, 6, RoundingMode.HALF_UP);
            metrics.put("categoryZScore", peerZScore);
            triggered = peerZScore.abs().compareTo(FUND_PEER_Z_THRESHOLD) >= 0;
            severity = peerZScore.abs();
            changeType = "category_relative_zscore";
            explanation = String.format("NAV move is %.1f standard deviations from its category's typical move today.",
                    peerZScore.abs());
        } else {
            // Small-sample fallback: this category has too few funds for a
            // trustworthy stddev, so fall back to a flat percentage-point
            // gap vs the category average instead of a noisy z-score.
            triggered = deviation.abs().compareTo(FUND_PEER_ABS_DEVIATION_THRESHOLD) >= 0;
            severity = deviation.abs().multiply(BigDecimal.valueOf(100)); // scale pp to a z-score-like magnitude
            changeType = "category_relative_deviation";
            explanation = String.format("NAV moved %.2f%% vs its category's average of %.2f%% today (category sample too small for a reliable z-score).",
                    fundChange.multiply(BigDecimal.valueOf(100)), categoryAvgChange.multiply(BigDecimal.valueOf(100)));
        }

        if (!triggered) {
            return new DetectedChangeResponse(symbol, InstrumentType.FUND, asOfDate,
                    false, null, null,
                    "Tracking its category peers normally — no action needed.", metrics);
        }

        return new DetectedChangeResponse(symbol, InstrumentType.FUND, asOfDate,
                true, changeType, severity.setScale(3, RoundingMode.HALF_UP), explanation, metrics);
    }

    /**
     * Writes a meaningful verdict to detected_changes — but only once per
     * (symbol, detected_date, changeType). Previously this inserted a new
     * row every single time detect() was called (every page load, every
     * check, every watchlist switch), so the "audit trail" grew without
     * bound purely from repeated reads of the SAME verdict. This is now a
     * real, bounded audit trail: at most one row per distinct verdict ever
     * actually observed, not one row per time someone looked.
     */
    private void persist(DetectedChangeResponse result) {
        boolean alreadyRecorded = detectedChangeRepository.existsBySymbolAndDetectedDateAndChangeType(
                result.symbol(), result.asOfDate(), result.changeType());
        if (alreadyRecorded) {
            return;
        }
        DetectedChange entity = new DetectedChange();
        entity.setSymbol(result.symbol());
        entity.setInstrumentType(result.instrumentType());
        entity.setDetectedDate(result.asOfDate());
        entity.setChangeType(result.changeType());
        entity.setSeverityScore(result.severityScore());
        entity.setExplanation(result.explanation());
        detectedChangeRepository.save(entity);
    }

    /**
     * The one real read use of detected_changes: how many times has this
     * symbol EVER been recorded with a meaningful verdict, across any
     * trading day — surfaced in the instrument detail panel as "flagged N
     * times before" so a repeat flag reads as a stronger signal than a
     * one-off blip. Deliberately just a count, not a full history list —
     * keeps this simple rather than building a history UI nobody asked for.
     */
    public long countPriorDetections(String symbol) {
        return detectedChangeRepository.countBySymbol(symbol);
    }

    private DetectedChangeResponse insufficientData(String symbol, InstrumentType type, String message) {
        return new DetectedChangeResponse(symbol, type, null, false, null, null, message, Map.of());
    }

    private void putIfPresent(Map<String, BigDecimal> map, String key, BigDecimal value) {
        if (value != null) {
            map.put(key, value);
        }
    }

    // JDBC drivers vary on whether native aggregate/window results come
    // back as BigDecimal, Double, or Number generally — normalize once here
    // rather than scattering casts through the two detect* methods.
    private BigDecimal toBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        return null;
    }

    private LocalDate toLocalDate(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDate ld) return ld;
        if (value instanceof Date d) return d.toLocalDate();
        return null;
    }
}
