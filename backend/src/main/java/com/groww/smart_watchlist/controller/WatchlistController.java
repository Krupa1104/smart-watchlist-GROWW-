package com.groww.smart_watchlist.controller;

import com.groww.smart_watchlist.dto.AddWatchlistItemRequest;
import com.groww.smart_watchlist.dto.CreateWatchlistRequest;
import com.groww.smart_watchlist.dto.SnapshotDiffResponse;
import com.groww.smart_watchlist.dto.WatchlistItemResponse;
import com.groww.smart_watchlist.dto.WatchlistResponse;
import com.groww.smart_watchlist.dto.WatchlistSummaryResponse;
import com.groww.smart_watchlist.service.WatchlistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

// userId is a required query param everywhere instead of a path segment or
// header — no auth in the MVP (see README §10), so this is an explicit,
// visible stand-in for "who is asking", not something dressed up to look
// like a session. Defaults conceptually to the seeded demo user (id=1),
// but the frontend must still pass it — no silent fallback here.
@RestController
@RequestMapping("/api/watchlists")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @PostMapping
    public ResponseEntity<WatchlistSummaryResponse> createWatchlist(
            @RequestParam Integer userId,
            @Valid @RequestBody(required = false) CreateWatchlistRequest request) {
        String name = request == null ? null : request.name();
        WatchlistSummaryResponse created = watchlistService.createWatchlist(userId, name);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public List<WatchlistSummaryResponse> listWatchlists(@RequestParam Integer userId) {
        return watchlistService.listWatchlists(userId);
    }

    @GetMapping("/{watchlistId}")
    public WatchlistResponse getWatchlist(
            @PathVariable Integer watchlistId,
            @RequestParam Integer userId) {
        return watchlistService.getWatchlist(watchlistId, userId);
    }

    // The explicit "checking" action: diffs current values against the last
    // snapshot, then updates the snapshot to now. Not the digest itself yet
    // (Phase 4 will wrap this with "meaningful" filtering + plain-language
    // explanations) — this returns the raw before/after per item so you can
    // verify persistence works before layering interpretation on top.
    @PostMapping("/{watchlistId}/check")
    public List<SnapshotDiffResponse> checkWatchlist(
            @PathVariable Integer watchlistId,
            @RequestParam Integer userId) {
        return watchlistService.checkWatchlist(watchlistId, userId);
    }

    @PostMapping("/{watchlistId}/items")
    public ResponseEntity<WatchlistItemResponse> addItem(
            @PathVariable Integer watchlistId,
            @RequestParam Integer userId,
            @Valid @RequestBody AddWatchlistItemRequest request) {
        WatchlistItemResponse added = watchlistService.addItem(watchlistId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(added);
    }

    @DeleteMapping("/{watchlistId}/items/{symbol}")
    public ResponseEntity<Void> removeItem(
            @PathVariable Integer watchlistId,
            @PathVariable String symbol,
            @RequestParam Integer userId) {
        watchlistService.removeItem(watchlistId, userId, symbol);
        return ResponseEntity.noContent().build();
    }
}
