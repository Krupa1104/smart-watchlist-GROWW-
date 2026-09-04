package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.WatchlistItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Integer> {
    List<WatchlistItem> findByWatchlistId(Integer watchlistId);
    Optional<WatchlistItem> findByWatchlistIdAndSymbol(Integer watchlistId, String symbol);
}
