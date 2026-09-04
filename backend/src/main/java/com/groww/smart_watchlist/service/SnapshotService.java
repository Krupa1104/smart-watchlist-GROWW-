package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.MarketDataResponse;
import com.groww.smart_watchlist.dto.SnapshotDiffResponse;
import com.groww.smart_watchlist.entity.WatchlistItem;
import com.groww.smart_watchlist.entity.WatchlistSnapshot;
import com.groww.smart_watchlist.repository.WatchlistSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class SnapshotService {

    private final WatchlistSnapshotRepository snapshotRepository;

    public SnapshotService(WatchlistSnapshotRepository snapshotRepository) {
        this.snapshotRepository = snapshotRepository;
    }

    /**
     * Diffs the item's current market data against whatever snapshot exists,
     * THEN overwrites the snapshot with the current value — in that order,
     * so the caller gets the "before" state before it's gone. This is the
     * one place in the app that mutates watchlist_snapshots.
     *
     * If marketData has no value yet (dataAvailable = false), the snapshot
     * is left untouched: there's nothing meaningful to remember as "seen".
     */
    @Transactional
    public SnapshotDiffResponse recordCheck(WatchlistItem item, MarketDataResponse marketData) {
        if (!marketData.dataAvailable() || marketData.latestValue() == null) {
            return new SnapshotDiffResponse(
                    item.getId(), item.getSymbol(), item.getInstrumentType(),
                    null, null, null, null, false, false
            );
        }

        Optional<WatchlistSnapshot> existing = snapshotRepository.findByWatchlistItemId(item.getId());
        BigDecimal previousValue = existing.map(WatchlistSnapshot::getLastSeenValue).orElse(null);
        OffsetDateTime previousViewedAt = existing.map(WatchlistSnapshot::getLastViewedAt).orElse(null);
        boolean firstView = existing.isEmpty();

        WatchlistSnapshot snapshot = existing.orElseGet(WatchlistSnapshot::new);
        snapshot.setWatchlistItem(item);
        snapshot.setLastSeenValue(marketData.latestValue());
        // lastViewedAt is @UpdateTimestamp on the entity — Hibernate stamps
        // it on this save automatically, insert or update.
        snapshotRepository.save(snapshot);

        return new SnapshotDiffResponse(
                item.getId(), item.getSymbol(), item.getInstrumentType(),
                previousValue, previousViewedAt,
                marketData.latestValue(), marketData.asOfDate(),
                firstView, true
        );
    }
}
