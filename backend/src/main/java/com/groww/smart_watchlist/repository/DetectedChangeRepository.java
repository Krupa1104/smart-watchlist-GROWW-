package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.DetectedChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;

public interface DetectedChangeRepository extends JpaRepository<DetectedChange, Integer> {
    List<DetectedChange> findBySymbolOrderByDetectedDateDesc(String symbol);

    // Used to dedupe writes (ChangeDetectionService.persist) — without
    // this, re-running detection for the same symbol/day/verdict (which
    // happens every time the frontend reloads a watchlist or re-checks)
    // would insert a fresh row every single time, growing this table
    // without bound for data nobody asked to see duplicated.
    boolean existsBySymbolAndDetectedDateAndChangeType(String symbol, LocalDate detectedDate, String changeType);

    // The one real read use of this table today — a lightweight "has this
    // been flagged before" signal surfaced in the instrument detail panel.
    // See InstrumentDetailResponse.priorDetectionCount.
    long countBySymbol(String symbol);
}
