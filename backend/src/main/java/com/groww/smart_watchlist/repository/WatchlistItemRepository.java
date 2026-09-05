package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.WatchlistItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface WatchlistItemRepository extends JpaRepository<WatchlistItem, Integer> {
    List<WatchlistItem> findByWatchlistId(Integer watchlistId);
    Optional<WatchlistItem> findByWatchlistIdAndSymbol(Integer watchlistId, String symbol);

    // Item counts for MULTIPLE watchlists in one query — replaces the N+1
    // pattern where WatchlistService.listWatchlists() used to call
    // findByWatchlistId(...).size() once per watchlist just to get a count.
    // Watchlists with zero items produce no row here (a plain GROUP BY
    // can't invent a zero), so callers must default missing ids to 0.
    @Query("SELECT i.watchlist.id, COUNT(i) FROM WatchlistItem i WHERE i.watchlist.id IN :watchlistIds GROUP BY i.watchlist.id")
    List<Object[]> countItemsGroupedByWatchlistId(@Param("watchlistIds") List<Integer> watchlistIds);

    // Re-fetches a single item WITH a row-level lock, so the caller can
    // safely read-then-write something keyed to this item (its snapshot)
    // without racing a concurrent request doing the same thing for the
    // SAME item. A second transaction calling this for the same id blocks
    // here until the first commits — then it re-reads the row's now-current
    // state, so it correctly sees the first transaction's write as its own
    // "previous" value, rather than a stale read taken before either wrote.
    // See WatchlistService.checkWatchlist() and SnapshotConcurrencyTest.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM WatchlistItem i WHERE i.id = :id")
    Optional<WatchlistItem> findByIdForUpdate(@Param("id") Integer id);
}
