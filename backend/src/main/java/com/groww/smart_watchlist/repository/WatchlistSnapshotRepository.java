package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.WatchlistSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface WatchlistSnapshotRepository extends JpaRepository<WatchlistSnapshot, Integer> {
    Optional<WatchlistSnapshot> findByWatchlistItemId(Integer watchlistItemId);
}
