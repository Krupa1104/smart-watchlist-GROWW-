package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.MarketDataResponse;
import com.groww.smart_watchlist.dto.SnapshotDiffResponse;
import com.groww.smart_watchlist.dto.WatchlistItemResponse;
import com.groww.smart_watchlist.dto.WatchlistResponse;
import com.groww.smart_watchlist.dto.WatchlistSummaryResponse;
import com.groww.smart_watchlist.entity.User;
import com.groww.smart_watchlist.entity.Watchlist;
import com.groww.smart_watchlist.entity.WatchlistItem;
import com.groww.smart_watchlist.exception.DuplicateItemException;
import com.groww.smart_watchlist.exception.ResourceNotFoundException;
import com.groww.smart_watchlist.exception.UnauthorizedAccessException;
import com.groww.smart_watchlist.repository.UserRepository;
import com.groww.smart_watchlist.repository.WatchlistItemRepository;
import com.groww.smart_watchlist.repository.WatchlistRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final UserRepository userRepository;
    private final MarketDataService marketDataService;
    private final SnapshotService snapshotService;

    public WatchlistService(WatchlistRepository watchlistRepository,
                             WatchlistItemRepository watchlistItemRepository,
                             UserRepository userRepository,
                             MarketDataService marketDataService,
                             SnapshotService snapshotService) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.userRepository = userRepository;
        this.marketDataService = marketDataService;
        this.snapshotService = snapshotService;
    }

    @Transactional
    public WatchlistSummaryResponse createWatchlist(Integer userId, String requestedName) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Watchlist watchlist = new Watchlist();
        watchlist.setUser(user);
        if (requestedName != null && !requestedName.isBlank()) {
            watchlist.setName(requestedName.trim());
        } // else: leave the entity's default ("My Watchlist")

        Watchlist saved = watchlistRepository.save(watchlist);
        return toSummary(saved, 0);
    }

    @Transactional(readOnly = true)
    public List<WatchlistSummaryResponse> listWatchlists(Integer userId) {
        ensureUserExists(userId);
        return watchlistRepository.findByUserId(userId).stream()
                .map(w -> toSummary(w, watchlistItemRepository.findByWatchlistId(w.getId()).size()))
                .toList();
    }

    @Transactional(readOnly = true)
    public WatchlistResponse getWatchlist(Integer watchlistId, Integer userId) {
        Watchlist watchlist = loadOwnedWatchlist(watchlistId, userId);
        List<WatchlistItem> items = watchlistItemRepository.findByWatchlistId(watchlistId);

        List<WatchlistItemResponse> itemResponses = items.stream()
                .map(item -> {
                    MarketDataResponse marketData =
                            marketDataService.getLatestMarketData(item.getSymbol(), item.getInstrumentType());
                    return new WatchlistItemResponse(
                            item.getId(), item.getSymbol(), item.getInstrumentType(),
                            item.getAddedAt(), marketData
                    );
                })
                .toList();

        LocalDate dataAsOf = itemResponses.stream()
                .map(i -> i.marketData().asOfDate())
                .filter(java.util.Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);

        return new WatchlistResponse(
                watchlist.getId(), watchlist.getUser().getId(), watchlist.getName(),
                watchlist.getCreatedAt(), dataAsOf, itemResponses
        );
    }

    @Transactional
    public WatchlistItemResponse addItem(Integer watchlistId, Integer userId, AddWatchlistItemRequest request) {
        Watchlist watchlist = loadOwnedWatchlist(watchlistId, userId);
        String symbol = request.symbol().trim().toUpperCase();

        if (!marketDataService.symbolExists(symbol, request.instrumentType())) {
            throw new ResourceNotFoundException(
                    "No " + request.instrumentType() + " found with symbol " + symbol);
        }

        watchlistItemRepository.findByWatchlistIdAndSymbol(watchlistId, symbol).ifPresent(existing -> {
            throw new DuplicateItemException(symbol + " is already on this watchlist");
        });

        WatchlistItem item = new WatchlistItem();
        item.setWatchlist(watchlist);
        item.setSymbol(symbol);
        item.setInstrumentType(request.instrumentType());
        WatchlistItem saved = watchlistItemRepository.save(item);

        MarketDataResponse marketData = marketDataService.getLatestMarketData(symbol, request.instrumentType());
        return new WatchlistItemResponse(saved.getId(), saved.getSymbol(), saved.getInstrumentType(),
                saved.getAddedAt(), marketData);
    }

    /**
     * The "I'm checking my watchlist now" action. For each item: fetch
     * current value, diff it against whatever was last seen, then overwrite
     * the snapshot with the current value. This is deliberately separate
     * from getWatchlist() (read-only) — see SnapshotService for why.
     * Phase 3's ChangeDetectionService will sit on top of this same method,
     * turning each raw diff into a "meaningful or not" verdict.
     */
    @Transactional
    public List<SnapshotDiffResponse> checkWatchlist(Integer watchlistId, Integer userId) {
        loadOwnedWatchlist(watchlistId, userId);
        List<WatchlistItem> items = watchlistItemRepository.findByWatchlistId(watchlistId);

        return items.stream()
                .map(item -> {
                    MarketDataResponse marketData =
                            marketDataService.getLatestMarketData(item.getSymbol(), item.getInstrumentType());
                    return snapshotService.recordCheck(item, marketData);
                })
                .toList();
    }

    @Transactional
    public void removeItem(Integer watchlistId, Integer userId, String symbol) {
        loadOwnedWatchlist(watchlistId, userId); // ownership check
        WatchlistItem item = watchlistItemRepository
                .findByWatchlistIdAndSymbol(watchlistId, symbol.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException(
                        symbol + " is not on this watchlist"));
        watchlistItemRepository.delete(item);
        // Note: watchlist_snapshots has ON DELETE CASCADE on watchlist_item_id,
        // so any Phase 2 snapshot row for this item is cleaned up automatically.
    }

    // --- helpers ---

    private void ensureUserExists(Integer userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResourceNotFoundException("User not found: " + userId);
        }
    }

    /**
     * Loads a watchlist and confirms it belongs to userId. There's no real
     * auth (see README) — this just stops one demo/user id from reading or
     * mutating another user's watchlist by guessing an id in the URL.
     */
    private Watchlist loadOwnedWatchlist(Integer watchlistId, Integer userId) {
        Watchlist watchlist = watchlistRepository.findById(watchlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Watchlist not found: " + watchlistId));
        if (!watchlist.getUser().getId().equals(userId)) {
            throw new UnauthorizedAccessException(
                    "Watchlist " + watchlistId + " does not belong to user " + userId);
        }
        return watchlist;
    }

    private WatchlistSummaryResponse toSummary(Watchlist watchlist, int itemCount) {
        return new WatchlistSummaryResponse(
                watchlist.getId(), watchlist.getName(), watchlist.getCreatedAt(), itemCount);
    }
}
