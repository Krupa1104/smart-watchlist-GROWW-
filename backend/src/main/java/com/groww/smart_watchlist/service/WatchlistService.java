package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.AttentionItemResponse;
import com.groww.smart_watchlist.dto.DetectedChangeResponse;
import com.groww.smart_watchlist.dto.InstrumentDetailResponse;
import com.groww.smart_watchlist.dto.MarketDataResponse;
import com.groww.smart_watchlist.dto.PricePointResponse;
import com.groww.smart_watchlist.dto.RelatedEventResponse;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class WatchlistService {

    private final WatchlistRepository watchlistRepository;
    private final WatchlistItemRepository watchlistItemRepository;
    private final UserRepository userRepository;
    private final MarketDataService marketDataService;
    private final SnapshotService snapshotService;
    private final ChangeDetectionService changeDetectionService;
    private final EventCorrelationService eventCorrelationService;
    private final SuggestionService suggestionService;

    public WatchlistService(WatchlistRepository watchlistRepository,
                             WatchlistItemRepository watchlistItemRepository,
                             UserRepository userRepository,
                             MarketDataService marketDataService,
                             SnapshotService snapshotService,
                             ChangeDetectionService changeDetectionService,
                             EventCorrelationService eventCorrelationService,
                             SuggestionService suggestionService) {
        this.watchlistRepository = watchlistRepository;
        this.watchlistItemRepository = watchlistItemRepository;
        this.userRepository = userRepository;
        this.marketDataService = marketDataService;
        this.snapshotService = snapshotService;
        this.changeDetectionService = changeDetectionService;
        this.eventCorrelationService = eventCorrelationService;
        this.suggestionService = suggestionService;
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

    /**
     * Runs detection for every item on the watchlist independently — this
     * is deliberately NOT tied to checkWatchlist()/snapshots. Detection
     * compares a symbol against its own history (stocks) or category peers
     * (funds), which has nothing to do with what this particular user last
     * saw. Phase 4's digest endpoint will combine this with checkWatchlist()
     * output to decide what to actually show the user.
     */
    @Transactional
    public List<DetectedChangeResponse> detectChanges(Integer watchlistId, Integer userId) {
        loadOwnedWatchlist(watchlistId, userId);
        List<WatchlistItem> items = watchlistItemRepository.findByWatchlistId(watchlistId);

        return items.stream()
                .map(item -> changeDetectionService.detect(item.getSymbol(), item.getInstrumentType()))
                .toList();
    }

    /**
     * Phase 4: the actual attention digest. Deliberately just a filter +
     * sort + reshape on top of detectChanges() — no new detection logic,
     * no second SQL pass. Reuses the exact same ChangeDetectionService
     * output that /detect exposes, so /attention and /detect can never
     * disagree about what's meaningful; /attention just narrows and ranks
     * it. Note this inherits detectChanges()'s persistence side effect
     * (meaningful verdicts get written to detected_changes) — same
     * trade-off /detect already makes, not a new one introduced here.
     */
    @Transactional
    public List<AttentionItemResponse> getAttentionItems(Integer watchlistId, Integer userId) {
        return detectChanges(watchlistId, userId).stream()
                .filter(DetectedChangeResponse::meaningful)
                .sorted(Comparator.comparing(DetectedChangeResponse::severityScore).reversed())
                .map(r -> new AttentionItemResponse(
                        r.symbol(), r.instrumentType(), r.asOfDate(), r.changeType(),
                        r.severityScore(), r.explanation(), r.metrics()))
                .toList();
    }

    /**
     * Backs the instrument detail panel (click a row in the watchlist
     * table). Deliberately just an assembly of things every other endpoint
     * here already computes — MarketDataService for current value/history,
     * ChangeDetectionService for the verdict (same detect() call /detect
     * itself uses, so the two can't disagree), EventCorrelationService and
     * SuggestionService for the two new narrowly-scoped additions — plus a
     * read-only "since last check" built straight from the item's existing
     * snapshot. No new detection logic lives here.
     */
    @Transactional
    public InstrumentDetailResponse getInstrumentDetail(Integer watchlistId, Integer userId, String symbol) {
        loadOwnedWatchlist(watchlistId, userId); // ownership check, same as every other endpoint
        String normalizedSymbol = symbol.trim().toUpperCase();
        WatchlistItem item = watchlistItemRepository
                .findByWatchlistIdAndSymbol(watchlistId, normalizedSymbol)
                .orElseThrow(() -> new ResourceNotFoundException(normalizedSymbol + " is not on this watchlist"));

        MarketDataResponse marketData = marketDataService.getLatestMarketData(normalizedSymbol, item.getInstrumentType());
        List<PricePointResponse> recentHistory = marketDataService.getRecentHistory(normalizedSymbol, item.getInstrumentType());
        DetectedChangeResponse detectedChange = changeDetectionService.detect(normalizedSymbol, item.getInstrumentType());
        Optional<RelatedEventResponse> relatedEvent =
                eventCorrelationService.findRelatedEvent(normalizedSymbol, item.getInstrumentType(), detectedChange.asOfDate());
        List<String> suggestedActions = suggestionService.suggestActions(detectedChange, relatedEvent);
        SnapshotDiffResponse sinceLastCheck = buildSinceLastCheck(item, marketData);

        return new InstrumentDetailResponse(
                normalizedSymbol, item.getInstrumentType(), marketData, recentHistory,
                detectedChange, sinceLastCheck, relatedEvent.orElse(null), suggestedActions
        );
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

    /**
     * Deletes a watchlist the user owns. watchlist_items and
     * watchlist_snapshots for it are removed by the database's own
     * ON DELETE CASCADE (see schema.sql) — stocks/funds/market data and
     * detected_changes are keyed by symbol, not by watchlist, so they are
     * never touched by this.
     */
    @Transactional
    public void deleteWatchlist(Integer watchlistId, Integer userId) {
        Watchlist watchlist = loadOwnedWatchlist(watchlistId, userId);

        List<WatchlistItem> items =
                watchlistItemRepository.findByWatchlistId(watchlistId);

        watchlistItemRepository.deleteAll(items);

        watchlistRepository.delete(watchlist);
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

    /**
     * Read-only counterpart to SnapshotService.recordCheck(): builds the
     * exact same SnapshotDiffResponse shape /check returns, but from
     * whatever snapshot already exists — WITHOUT overwriting it. Viewing an
     * instrument's detail panel must never move the user's "last seen"
     * baseline; only the explicit /check action is allowed to do that.
     */
    private SnapshotDiffResponse buildSinceLastCheck(WatchlistItem item, MarketDataResponse marketData) {
        if (!marketData.dataAvailable() || marketData.latestValue() == null) {
            return new SnapshotDiffResponse(
                    item.getId(), item.getSymbol(), item.getInstrumentType(),
                    null, null, null, null, false, false
            );
        }
        return snapshotService.peekSnapshot(item.getId())
                .map(snap -> new SnapshotDiffResponse(
                        item.getId(), item.getSymbol(), item.getInstrumentType(),
                        snap.getLastSeenValue(), snap.getLastViewedAt(),
                        marketData.latestValue(), marketData.asOfDate(),
                        false, true
                ))
                .orElseGet(() -> new SnapshotDiffResponse(
                        item.getId(), item.getSymbol(), item.getInstrumentType(),
                        null, null,
                        marketData.latestValue(), marketData.asOfDate(),
                        true, true
                ));
    }
}