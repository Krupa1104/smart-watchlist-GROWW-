# Smart Watchlist

> Groww shows me what my watchlist contains. Smart Watchlist tells me what changed, why it matters, and what deserves my attention.

A full-stack (React + Spring Boot + PostgreSQL) watchlist that goes past "here's today's price" and tries to answer the question every investor actually asks when they open an app: **"has anything happened since I last looked, and does it matter?"**

**Live:** Frontend → https://smart-watchlist-groww.vercel.app/ · Backend → https://smart-watchlist-groww.onrender.com/ (free-tier backend sleeps when idle — the first request can take ~30–60s to wake it; see §17)

---

## Part 1 — Overview (2-minute read)

### The problem
A normal watchlist is a mirror: it shows you today's price and today's % change, and leaves the "is this normal?" judgment entirely to you. It doesn't remember what you saw last time, doesn't know if today's move is unusual for *that specific instrument*, and doesn't tell you if anything you can actually verify happened to cause it.

### What Smart Watchlist does differently
For every instrument on your watchlist, the system:
1. Compares **today's move against that instrument's own recent history** (a return z-score for stocks, a peer-relative comparison for funds) — not a flat "moved more than X%" rule.
2. Remembers the **value you last checked**, so returning later shows a real "since your last check" diff, not just "today's number."
3. Tries to connect a flagged move to a **real planted market event** — and says so explicitly when it can't, rather than inventing a reason.
4. Offers a short list of **evidence-based, non-prescriptive next steps** ("review the related event," "check volume") — never a buy/sell call.
5. Runs a **clearly-labeled simulated intraday feed** over SSE so the demo actually feels live, without pretending to be a real market data provider.

### Core user flow
```
Create/select a watchlist
      ↓
Add stocks/funds (autocomplete search)
      ↓
Watch simulated live prices tick in real time
      ↓
Click "Check for changes" → see genuine since-last-check diffs
      ↓
"What changed" surfaces only the statistically unusual instruments
      ↓
Click an instrument → detail panel: history, anomaly explanation,
related event (or an honest "no event found"), suggested next steps
```

### Main technologies
| Layer | Tech |
|---|---|
| Frontend | React 18 + Vite, plain CSS (no UI/chart/state-mgmt library) |
| Backend | Spring Boot 4.1.1 (Web MVC + Data JPA + Validation), Java 17 |
| Database | PostgreSQL |
| Live feed | Server-Sent Events (`SseEmitter` + browser `EventSource`) — no WebSockets |
| Data | A generated synthetic 6-month OHLCV/NAV dataset with 10 planted ground-truth events |
| Deployment | Vercel (frontend) + Render/Docker (backend) + Neon (PostgreSQL) — all free tier (§17) |

### Simulated vs. real
- **Real (persisted in PostgreSQL):** every stock/fund's daily OHLCV or NAV history, the 10 planted market events, all watchlists/items/snapshots.
- **Simulated (in-memory only, never persisted):** the "live" intraday price you see ticking — a bounded random walk seeded from each instrument's real latest daily close/NAV, clearly labeled **"Simulated live data"** everywhere it appears. It is not real NSE/BSE data and does not pretend to be.

### Major features implemented
Watchlist CRUD (create with a custom name, add/remove instruments, delete) · global instrument search + in-watchlist filter · statistical anomaly detection (stocks vs. own history, funds vs. category peers) · a real "since your last check" diff · event correlation with honest no-match handling · non-prescriptive suggested actions · an instrument detail panel with a daily-history sparkline · a simulated live/intraday feed over SSE with visible reconnect state · a severity legend explaining the whole scheme in plain language.

---

## Part 2 — Detailed Documentation

### 1. Problem Interpretation

**Why raw % change isn't enough.** A 3% move means very different things for a low-volatility banking stock and a small-cap stock that swings 3% most days. Flat percentage thresholds either cry wolf constantly for volatile names or miss real moves in quiet ones.

**What "meaningfully changed" means here, concretely:**
- **Stocks** — a **return z-score**: today's daily return compared against that stock's own trailing 20-day mean/stddev of returns (`ChangeDetectionService`, `STOCK_RETURN_Z_THRESHOLD = 2.0`), plus a **volume ratio** vs. its own trailing 20-day average volume (`VOLUME_SPIKE_RATIO_THRESHOLD = 2.0×`). Either signal alone can trigger "meaningful"; both together raise severity and combine the explanation.
- **Funds** — funds move slower and smoother than stocks by nature, so comparing a fund to its *own* history isn't the right lens. Instead each fund's daily NAV change is compared to its **category peers'** average change the same day (`FUND_PEER_Z_THRESHOLD = 1.5σ`). With only 3 funds per category in this dataset, a 2-3 point stddev is statistically thin — below `MIN_CATEGORY_SAMPLE_FOR_ZSCORE = 5`, the code deliberately falls back to a flat percentage-point deviation (`FUND_PEER_ABS_DEVIATION_THRESHOLD = 1pp`) rather than trusting a noisy z-score.
- **Severity** is the z-score magnitude itself (or the raw volume ratio, for a volume-only trigger) — a continuous number, not a fixed "high/medium/low" bucket baked into the backend. The frontend buckets it into **Minor / Attention / High attention** purely for display (see `AttentionCard.jsx`'s `severityTier`).
- **"No recorded event" is a real, displayed outcome**, not an error state — with only 10 planted events across 51 instruments over 126 days, most detected anomalies legitimately have nothing to correlate to, and the UI says so explicitly rather than implying every flagged move is explained.

### 2. Features (as actually implemented)

| Feature | Where |
|---|---|
| Create watchlist with a custom name (validated, non-empty) | `CreateWatchlistModal.jsx` → `POST /api/watchlists` |
| List/switch between multiple watchlists | `WatchlistTabs.jsx` → `GET /api/watchlists` |
| Delete a watchlist (cascades items/snapshots) | `WatchlistToolbar.jsx` → `DELETE /api/watchlists/{id}` |
| Add a stock/fund (validated to actually exist) | `AddItemForm.jsx` → `POST /api/watchlists/{id}/items` |
| Remove an instrument (edit mode) | `WatchlistTable.jsx` → `DELETE /api/watchlists/{id}/items/{symbol}` |
| Global instrument search/autocomplete | `AppHeader.jsx`, filters a client-cached `GET /api/instruments` list — no per-keystroke API calls |
| In-watchlist filter (by symbol/name/sector) | `WatchlistToolbar.jsx`, client-side only |
| Latest price/NAV display | `WatchlistTable.jsx`, from `MarketDataResponse` |
| Historical 1D change (real, not simulated) | `WatchlistTable.jsx`'s "1D change" column, from `/detect`'s metrics |
| "What changed" digest | `AttentionSection.jsx` → `GET /api/watchlists/{id}/attention` |
| "Since your last check" (real diff, not today's number) | `SinceLastCheckPanel.jsx`, built from `POST /api/watchlists/{id}/check` |
| Statistical anomaly detection + severity + plain-language explanation | `ChangeDetectionService` → `/detect` |
| Severity legend (explains Minor/Attention/High + what σ means) | `SeverityLegend.jsx` |
| Event correlation ("related event" or honest "no event found") | `EventCorrelationService`, surfaced in the detail panel |
| Evidence-based suggested actions | `SuggestionService`, surfaced in the detail panel |
| Instrument detail panel (price, daily-history sparkline, since-last-check, detected change, related event, suggestions) | `InstrumentDetailPanel.jsx` → `GET /api/watchlists/{id}/items/{symbol}/detail` |
| Simulated live/intraday feed | `TickSimulationService` → SSE → `liveFeed.js` |
| Live-feed connection badge + reconnect state | `WatchlistToolbar.jsx` ("Simulated live data" / "Reconnecting to live feed…") |
| Loading / error / empty states | `StatusStates.jsx`, used throughout |

### 3. User Flow / Demo Flow

1. **Land on the app** — the demo user's watchlist(s) load; the table shows each instrument's latest price/NAV and real 1D change.
2. **Create or select a watchlist** — name it explicitly via the modal; switching tabs opens exactly one new SSE connection and closes the previous one.
3. **Add instruments** — via the search autocomplete or the add form; both validate the symbol actually exists server-side.
4. **Watch the live badge** — prices tick every ~1 second (simulated), clearly labeled; if the connection drops, the badge switches to "Reconnecting…" and back automatically.
5. **Click "Check for changes"** — this is the one deliberate, explicit action that reads the previous snapshot, diffs it against the current (simulated) value, and overwrites the snapshot — surfaced in the "Since your last check" panel with real ₹ and % deltas.
6. **Look at "What changed"** — only statistically unusual instruments appear here, ranked by severity.
7. **Click a flagged (or any) instrument** — opens the detail panel: recent daily history, the detection verdict and its metrics, the since-last-check comparison, a related event if one exists (or an honest "no recorded event"), and a short suggested-actions list.

### 4. Architecture

```
Frontend (React + Vite)
   │
   ├── REST (fetch) ──────────────► Spring Boot controllers ──► services ──► PostgreSQL
   │                                                                          (stocks, funds, prices, NAVs,
   │                                                                           watchlists, snapshots, events)
   │
   └── SSE (EventSource) ─────────► TickSimulationService (in-memory)
                                     seeded from the same PostgreSQL price/NAV history,
                                     never writes ticks back to the database
```

Two independent data paths worth calling out explicitly, because keeping them separate is what makes the anomaly detection trustworthy:
- **Anomaly/1D-change path**: `ChangeDetectionService` queries `stock_prices`/`fund_navs` directly via native SQL — it has no dependency on the simulated feed at all.
- **"Current price" path**: `MarketDataService` sources the displayed price from `TickSimulationService`, which is what lets the watchlist table and "since last check" reflect the ticking simulated value automatically, without any change to the check/snapshot logic itself.

### 5. Backend

**Entities** (`entity/`): `User`, `Stock`, `Fund`, `StockPrice`, `FundNav`, `Watchlist`, `WatchlistItem`, `WatchlistSnapshot`, `MarketEvent`, `DetectedChange`, `InstrumentType` (enum).

**Repositories** (`repository/`): one Spring Data JPA interface per entity, plus two native-SQL queries that do the actual statistics: `StockPriceRepository.findLatestStockSignal` (rolling 20-day return z-score + volume ratio) and `FundNavRepository.findLatestFundPeerSignal` (category-relative NAV change). Both also have an `*AsOf` variant used only by the ground-truth tests, to evaluate a historical date without touching production `/detect` behavior.

**Controllers**: `WatchlistController` (everything watchlist-scoped: CRUD, check, detect, attention, item add/remove, instrument detail, the `/live` SSE stream) and `InstrumentController` (`GET /api/instruments`, the full reference list backing global search).

**Services**:
- `MarketDataService` — the only place that knows STOCK reads from `stock_prices` and FUND from `fund_navs`; also the seam where "current price" is sourced from the simulated feed. `getLatestMarketDataBatch()` fetches every stock/fund entity for a whole watchlist in two queries total, not one per item — the single batch path now shared by `getWatchlist()`, `detectChanges()`, **and `checkWatchlist()`** (see §11/§13).
- `SnapshotService` — owns `watchlist_snapshots`. `recordCheck()` reads the existing snapshot's value **before** overwriting it (so the diff is real), then writes the new value; `peekSnapshot()` is a read-only variant used by the detail panel so merely *viewing* an instrument never moves your "last seen" baseline. Concurrent checks on the same item are serialized by a pessimistic row lock taken in `WatchlistService.checkWatchlist()` before this runs — see §11/§13.
- `ChangeDetectionService` — the anomaly engine described in §1; persists only *meaningful* verdicts to `detected_changes` as a deduplicated audit trail (at most one row per distinct symbol/date/changeType ever observed, not one per time someone looked). `detectBatch()` computes every stock's signal in a single query for the whole watchlist; `countPriorDetections()` is the table's one read use, surfaced in the detail panel.
- `EventCorrelationService` — matches a detected change's date to a planted `MarketEvent` on the same symbol, within a ±3-day window (`CORRELATION_WINDOW_DAYS`). **Known limitation:** sector-scope events (e.g. "IT sector rallies") are stored with a `NULL` symbol column by the data loader (see `schema.sql`'s own comment), so this service only correlates direct stock/fund-symbol events — a member stock of an affected sector will correctly show "no recorded event" rather than a guessed match.
- `SuggestionService` — rule-based (no ML), only produces output for meaningful changes, and its own test suite explicitly checks that no suggestion ever contains "buy", "sell", "invest", or similar prescriptive language.
- `TickSimulationService` — see §10.
- `WatchlistService` — the orchestration layer every controller method actually calls into; owns the ownership check (`loadOwnedWatchlist`) used by every endpoint. `checkWatchlist()` locks every item (`findByIdForUpdate`, same order and fallback as before) before fetching market data for all of them in one batched call, rather than one lookup per item — see §11/§13.

**DTOs** (`dto/`): one record per response/request shape — `WatchlistResponse`, `WatchlistSummaryResponse`, `WatchlistItemResponse`, `MarketDataResponse`, `SnapshotDiffResponse`, `DetectedChangeResponse`, `AttentionItemResponse`, `InstrumentDetailResponse`, `RelatedEventResponse`, `PricePointResponse`, `LiveTickResponse`, `InstrumentSummaryResponse`, plus the two request DTOs (`CreateWatchlistRequest`, `AddWatchlistItemRequest`).

### 6. Frontend

- **`App.jsx`** — owns all top-level state and is the single place that opens the SSE connection (see §10), so it can never be duplicated by a child component.
- **`WatchlistTable.jsx`** — the dense instrument table; prefers the live simulated price when one has arrived, while the "1D change" column stays keyed to the real `/detect` metrics regardless.
- **`AttentionSection.jsx` / `AttentionCard.jsx`** — "What changed", with a loading/error/empty state (`StatusStates.jsx`) for "nothing important changed" as a normal, non-error outcome.
- **`SinceLastCheckPanel.jsx`** — the detailed per-instrument since-last-check breakdown (previous → current, ₹/% delta, time elapsed, volume ratio, Normal/Unusual).
- **`SeverityLegend.jsx`** — a plain-language explainer for Minor/Attention/High attention and what σ means.
- **`InstrumentDetailPanel.jsx`** — slide-over panel; builds its own SVG sparkline from `PricePointResponse[]` (no charting library), a second, separately-labeled live intraday sparkline from ticks received while it's open, and a "flagged N times before" note sourced from `detected_changes`' one real read use.
- **`WatchlistToolbar.jsx`** — search, Add/Edit toggles, "Check for changes", and the live-feed status badge (green "Simulated live data" / amber "Reconnecting to live feed…").
- **`CreateWatchlistModal.jsx`, `AddItemForm.jsx`, `WatchlistTabs.jsx`, `AppHeader.jsx`** — creation, adding, tab-switching, and global search UI.
- Responsive behavior is handled with a single `@media (max-width: 720px)` breakpoint in `index.css` (header wraps, table scrolls horizontally, detail panel goes full-width) — no responsive framework.

### 7. Data

- **Persisted (PostgreSQL):** `stocks`, `funds`, `stock_prices` (OHLCV, one row per symbol per trading day), `fund_navs` (NAV only — funds don't trade intraday), `market_events` (the 10 planted events), `users`, `watchlists`, `watchlist_items`, `watchlist_snapshots`, `detected_changes` (the detector's own audit trail — distinct from `market_events`, the ground truth).
- **Synthetic dataset:** generated by `data-generator/generate_market_data.py` — 36 stocks (6 sectors × 6), 15 funds (5 categories × 3), 126 trading days from 2026-03-02, with sector/category-correlated factors so sector-wide moves emerge naturally, plus the 10 deliberately-injected storyline events (`events.json`).
- **In-memory only, never persisted:** every simulated tick from `TickSimulationService` — see §10 for exactly why.

### 8. Anomaly Detection

Already detailed in §1; to restate precisely: **stocks** use a **return z-score against the instrument's own trailing 20-day history**, combined with a **volume ratio against its own trailing 20-day average volume**; **funds** use a **category-relative NAV z-score** (or a flat percentage-point fallback when the category sample is too small to trust a stddev). This is deterministic, threshold-based statistics — **not machine learning**, and the repository contains no ML model, training code, or inference dependency.

### 9. Event Correlation & Suggested Actions

Event correlation (§5) only ever claims a match within a ±3-day window on the exact symbol; everything else is explicitly presented as **"No recorded event — statistical anomaly only."** rather than a guessed explanation.

Suggested actions (`SuggestionService`) follow a simple philosophy:
- Only generated for changes already flagged **meaningful** — a normal move has nothing to suggest.
- Every suggestion is a **next investigative step**, e.g. "Review the related event below," "Monitor this instrument closely," "Check whether this is a short-lived volume spike," "Consider reviewing your position or risk exposure" for very large moves.
- **Never** a buy/sell/hold call, a price prediction, or an execution action — there is no order/trade endpoint anywhere in this repository.

### 10. Live Feed / SSE

**Why simulated, not real market data:** there's no live NSE/BSE feed to connect to for a hackathon dataset, and the brief explicitly asks not to fabricate real-looking market data — so the feed is bounded, clearly labeled, and built entirely from data already in PostgreSQL.

**How `TickSimulationService` works:**
- The **first time** any code asks about a symbol (a REST call or an SSE subscription), its state is seeded from that symbol's **real latest daily OHLC row** (stocks) or **latest NAV** (funds) — so nothing looks different until at least one tick has run.
- Every ~1 real second (`@Scheduled(fixedRate = 1000)`), each *currently-watched* symbol's price takes one small bounded random step: for stocks, up to 5% of that day's real (high − low) range; for funds (which have no OHLC), a synthetic ±1% band around the latest NAV, reflecting that funds move slower/smoother than stocks. Every step is clamped back inside those bounds — it cannot drift outside a realistic range.
- **Ticks are never written to PostgreSQL** — this is explicitly in-memory, per-symbol state (`ConcurrentHashMap`), because persisting every simulated tick would be meaningless data bloat for a value that isn't real.
- **SSE delivery**: one `GET /api/watchlists/{id}/live` connection per watchlist, batched into **one event per watchlist per second** (not one event per instrument), so a 12-item watchlist doesn't mean 12× the traffic.
- **One active subscription per watchlist page**: the frontend opens the connection once in `App.jsx`, keyed to the selected watchlist, and closes it before opening a new one on switch (and on unmount) — verified by dedicated frontend tests.
- **Reconnect behavior**: no custom polling or retry logic was written — the browser's native `EventSource` auto-retry is what's actually reconnecting. The app only *listens* to that (`onopen`/`onerror`) to show "Reconnecting to live feed…" instead of silently looking live, then reverts automatically once `onopen` fires again.
- **Subscriber cleanup**: a real client disconnect is detected by Spring's own async request machinery and fires the registered completion/timeout/error callbacks; a direct `.complete()`/`.completeWithError()` call (e.g. from a failed send) is also handled via a small `TrackedEmitter` subclass that deregisters synchronously either way, so a subscriber can never leak.

### 11. Engineering Decisions & Trade-offs

| Decision | Why |
|---|---|
| No authentication | Out of scope for a 72-hour hackathon; every endpoint already takes an explicit `userId` param and enforces ownership (`loadOwnedWatchlist` → 403/404), so real auth would slot in by replacing *how* `userId` is derived, not by restructuring the API. |
| No real trading/order execution | Not the problem being solved — this is a "what changed and why" tool, not a brokerage. |
| No real market-data API | A hackathon-scale synthetic dataset with known ground truth (planted events) is more useful for proving detection actually works than hoping real markets do something interesting during the build window. |
| No ML | The detection is deterministic statistics (z-scores, category comparisons) — genuinely explainable in a 5-minute demo, which a black-box model wouldn't be. |
| SSE, not WebSockets | Only server→browser updates are needed; `SseEmitter` + `EventSource` are already bundled with `spring-boot-starter-webmvc` and the browser respectively — no new dependency for a one-directional need. |
| In-memory tick simulation | Single hackathon instance, no multi-node coordination problem exists yet to solve (see §12 / `docs/SCALABILITY.md`). |
| Snapshot state is stateless-server + PostgreSQL, genuinely multi-device | `watchlist_snapshots` is the single source of truth in the DB — any device/browser hitting the same `userId` sees the same state, since nothing is cached in server memory per-session. Concurrent writes to the SAME item (two tabs, two devices, a double-click) are handled explicitly: `checkWatchlist()` takes a pessimistic row lock (`findByIdForUpdate`) on the item before reading/writing its snapshot, so a second concurrent check correctly serializes behind the first rather than racing it — see §13. |
| Sector-scope events not correlated | The data loader stores `NULL` for the symbol column on sector-scope rows (by design, per `schema.sql`), so there's no queryable link from a sector event to its member stocks without either a schema change or fragile text-parsing of the event description — neither of which was judged worth it for 2 of the 10 events. |
| Batched DB lookups for larger watchlists, not a full rewrite | `getWatchlist()`, `listWatchlists()`, `detectChanges()`, and **`checkWatchlist()`** used to issue one query per item. All four now batch (2 queries total regardless of watchlist size for market data/item counts; a single `PARTITION BY symbol` query for stock detection, verified to produce numerically identical results to the old per-symbol query). `checkWatchlist()` was the last of the four to be fixed — it still locks each item individually (`findByIdForUpdate`, unchanged — see the concurrency row below), but now fetches every locked item's market data in one batched call instead of one per item, falling back to the single-item lookup only if a symbol is somehow missing from the batch result. Fund detection deliberately stays per-symbol — batching its category-peer comparison safely would need a materially more complex rewrite, and funds are a minority of a typical watchlist (15 of 51 instruments in this dataset). |
| `detected_changes` writes are deduplicated, and now have one real read use | Originally this table gained a new row every single time a meaningful verdict was recomputed (every page load, every check) — unbounded growth for data nobody asked to see duplicated. `persist()` now dedupes on (symbol, date, changeType), and the table has one genuine, lightweight read use: a "flagged N times before" count shown in the instrument detail panel — not a fake feature, just giving already-collected data an actual purpose. |

### 12. Scalability

*(Source: `docs/SCALABILITY.md`, integrated here.)*

**Current state: single-instance, in-memory.** `TickSimulationService` keeps both its tick state (`ConcurrentHashMap<String, TickState>`) and its SSE subscriber lists (`ConcurrentHashMap<Integer, CopyOnWriteArrayList<SseEmitter>>`) in plain JVM memory. This is acceptable because the hackathon runs as a single backend instance — there is no coordination problem to solve yet.

**Where it breaks with more than one instance:** each instance would independently seed and random-walk its own copy of every symbol's price (two users on different instances would see diverging "live" prices for the same symbol), and an SSE connection is pinned to whichever instance accepted it — a tick generated on instance A never reaches a subscriber on instance B.

**The production fix — not currently implemented:**
1. Move tick *generation* out of per-instance state into a single shared source of truth — a **Redis Pub/Sub** channel (or equivalent broker) that one designated instance/worker publishes to, keyed by watchlist/symbol.
2. **Every backend instance subscribes** to that channel and fans each message out only to its own **locally-connected** SSE emitters — the browser-facing fan-out logic doesn't need to change, only where the tick data comes from.
3. Subscriber bookkeeping stays local per instance (an emitter only ever belongs to the instance that accepted its connection) — only the tick data feeding it needs to become shared.

**No Redis, Kafka, or WebSockets are implemented in this repository** — this section describes the seam the design already anticipates, not a built feature.

### 13. Reliability / Edge Cases

| Case | Handling |
|---|---|
| Concurrent "Check for changes" on the same item (two tabs/devices, a double-click) | `checkWatchlist()` takes a pessimistic row lock on the item (`findByIdForUpdate`) before reading/writing its snapshot — the second call blocks until the first commits, then correctly sees the first's write as its own "previous" value, rather than racing a stale read. Covered by `SnapshotConcurrencyTest` (real multi-threaded test, not a mocked race). |
| Concurrent "add this symbol" on the same watchlist | The same-request duplicate check (`DuplicateItemException` → 409) can't fully close a true race between two simultaneous adds; the database's own unique constraint is the real backstop, and a `GlobalExceptionHandler` mapping turns that constraint violation into the same clean 409 instead of a raw 500. |
| Any unexpected/unmapped server error (a bug, a dependency failure, anything not one of the specific exceptions above) | A catch-all `GlobalExceptionHandler` mapping (`Exception` → HTTP 500) returns the same fixed, generic error-response shape as every other handler — the real exception is logged server-side, but the client never sees the exception's message or a stack trace. Covered by `GlobalExceptionHandlerTest`. |
| SSE disconnect/reconnect | Browser auto-retry + visible "Reconnecting…" badge (§10) |
| SSE subscriber cleanup | `TrackedEmitter` + completion/timeout/error callbacks (§10) — covered by `TickSimulationServiceTest` |
| Stale/deleted watchlist still selected in the UI | On a 404 from add/remove/check, the frontend re-fetches the watchlist list and drops the dead selection automatically |
| Invalid ownership (another user's watchlist id) | `UnauthorizedAccessException` → HTTP 403, on every watchlist-scoped endpoint |
| Watchlist/symbol not found | `ResourceNotFoundException` → HTTP 404 |
| Duplicate instrument on the same watchlist (same request) | `DuplicateItemException` → HTTP 409 |
| Empty watchlist | Explicit "no instruments yet" empty state, not a blank table |
| No watchlists at all | Explicit "create one to start tracking" empty state |
| First-ever check on an item (no baseline) | Surfaced as "baseline recorded," not a fabricated 0% diff |
| Small fund-category sample (< 5 funds) | Falls back to a flat percentage-point deviation instead of an unreliable z-score |
| Insufficient trailing history (new-ish symbol) | Detector returns "not enough history yet" rather than a false verdict |
| Detail panel viewed but not "checked" | `peekSnapshot()` is read-only — opening the panel never moves your last-seen baseline |
| Global search vs. watchlist search | Kept as two independent state variables/inputs so typing in one never filters the other |
| Larger watchlists (more items per watchlist) | `getWatchlist()`/`listWatchlists()`/`detectChanges()`/`checkWatchlist()` batch their DB lookups instead of querying per item — see §11 and §14's regression tests |

### 14. Testing

**Backend** — 17 JUnit 5 test classes, 65 declared `@Test`-annotated methods (one of which is a `@ParameterizedTest` expanding across the 6 planted stock-scope events — so ~70 test executions when actually run — and one intentionally `@Disabled` with an explanatory comment documenting the sector-event limitation from §5/§11 rather than silently skipping it). Two of the 17 classes (`CheckWatchlistBatchRegressionTest`, `GlobalExceptionHandlerTest`) were added specifically to cover the two targeted fixes below (§11/§13):

| Test class | Covers |
|---|---|
| `ChangeDetectionServiceGroundTruthTest` | All 6 planted stock events are actually flagged meaningful; documents the fund/sector-event detection limitations |
| `WatchlistAttentionTest` | The real seeded demo watchlist's attention digest matches expected meaningful/non-meaningful instruments, ordering, and field completeness |
| `WatchlistDeletionTest` | Deletion cascades correctly, ownership is enforced, unrelated watchlists are untouched |
| `MarketDataInstrumentListingTest` | The global search reference list matches the real 36+15 seeded instruments |
| `EventCorrelationServiceTest` | Direct stock/fund event matches, correlation window boundaries, and the documented sector-event limitation |
| `SuggestionServiceTest` | Suggestions only appear for meaningful changes and never contain prescriptive language |
| `WatchlistServiceInstrumentDetailTest` | Detail panel assembly, read-only since-last-check behavior, ownership checks, `priorDetectionCount` wiring |
| `SnapshotDiffAccuracyTest` | Regression coverage proving the since-last-check diff is computed from the pre-check snapshot value, not the post-check one — including a positive and a negative move |
| `TickSimulationServiceTest` | Tick bounds stay within real OHLC/NAV ranges, the walk actually moves, `asOfDate` stability, subscriber count bookkeeping on complete/error |
| `WatchlistServiceLiveTicksTest` | Ownership enforcement on the `/live` SSE endpoint |
| `SnapshotConcurrencyTest` | **Real multi-threaded** regression test (not mocked) proving concurrent "Check for changes" calls on the same item never race — exactly one concurrent first-ever check wins and reports `firstView=true`, the rest correctly serialize behind it |
| `ChangeDetectionBatchEquivalenceTest` | The batched stock-detection query produces numerically identical verdicts to the old per-symbol query, across a cross-section including a known-anomalous symbol |
| `BatchQueryRegressionTest` | Batched market-data lookups and grouped watchlist item counts match the old per-item approach exactly, including a zero-item watchlist |
| `DetectedChangeAuditTrailTest` | Repeatedly detecting the same verdict does not grow `detected_changes`; a never-flagged symbol has zero prior detections |
| `CheckWatchlistBatchRegressionTest` | `checkWatchlist()`'s batched market-data fetch produces the exact same values as the old per-item lookup, across a mixed stock+fund watchlist, a first check and a second check, a single-item watchlist, and an empty watchlist |
| `GlobalExceptionHandlerTest` | An unexpected/unmapped exception maps to a clean, generic HTTP 500 — proves the client-facing message is always the fixed fallback text and never the real exception's message, and that the response body never grows an unexpected field (no stack trace) |
| `SmartWatchlistApplicationTests` | Spring context loads |

A prior local run reported 56 executed tests with 2 failures, both isolated to SSE subscriber cleanup in `TickSimulationServiceTest` (an emitter completed directly in a unit test, outside a live HTTP request, wasn't deregistering). That was root-caused and fixed by having `subscribe()` return a small `TrackedEmitter` that deregisters synchronously on `complete()`/`completeWithError()`, in addition to the existing completion callbacks used for real client disconnects. The full suite — including `CheckWatchlistBatchRegressionTest` and `GlobalExceptionHandlerTest`, added alongside the `checkWatchlist()` batching fix and the generic-500 handler — has since been run and passes.

**Frontend** — 47 test cases in `App.test.jsx` (Vitest + React Testing Library), grouped into: core user journey (watchlist load/add/remove/edit/delete/check), global instrument search, stale-watchlist recovery, since-last-check panel, severity legend, instrument detail panel (including the detection-history count), and the simulated live feed (subscription lifecycle, reconnect badge, table price updates without touching the 1D-change column). `npm run build` succeeds.

### 15. What We Deliberately Did NOT Build

- **Real trading/order execution** — this is a "what changed and why" tool, not a brokerage; adding order flow would be a different product.
- **Authentication** — out of hackathon scope; the ownership model (`userId` + `loadOwnedWatchlist`) is already structured so real auth slots in later without an API redesign.
- **Payments** — no product surface here needs them.
- **A real market-data provider** — a synthetic dataset with planted, known ground truth lets detection be *proven* correct, which real market noise wouldn't allow in a 72-hour window.
- **Machine learning** — the detection is deterministic, explainable statistics; a black-box model would be harder to defend live and isn't needed to answer "is this unusual for this instrument."
- **Large charting/trading UI (index tickers, 52-week ranges, order books, etc.)** — the backend has no index or historical-series endpoint to back these, and fabricating values for them would violate the project's own "don't invent market data" principle (see `frontend/README.md`).

Each of these was a deliberate prioritization decision around the actual problem statement, not an oversight.

### 16. Project Structure

```
smart-watchlist-GROWW/
├── backend/
│   ├── src/main/java/com/groww/smart_watchlist/
│   │   ├── controller/        WatchlistController, InstrumentController
│   │   ├── service/           WatchlistService, MarketDataService, SnapshotService,
│   │   │                      ChangeDetectionService, EventCorrelationService,
│   │   │                      SuggestionService, TickSimulationService
│   │   ├── entity/            User, Stock, Fund, StockPrice, FundNav, Watchlist,
│   │   │                      WatchlistItem, WatchlistSnapshot, MarketEvent,
│   │   │                      DetectedChange, InstrumentType
│   │   ├── repository/        one Spring Data JPA repo per entity
│   │   ├── dto/                request/response records
│   │   ├── exception/          + GlobalExceptionHandler
│   │   └── config/             WebConfig (CORS)
│   ├── src/test/java/...       17 test classes (see §14)
│   ├── Dockerfile               multi-stage build (Maven→JRE) used by the Render deployment (see §17)
│   └── .dockerignore
├── frontend/
│   └── src/
│       ├── App.jsx              top-level state, SSE connection owner
│       ├── components/          WatchlistTable, AttentionSection, SinceLastCheckPanel,
│       │                        SeverityLegend, InstrumentDetailPanel, WatchlistToolbar,
│       │                        CreateWatchlistModal, AddItemForm, WatchlistTabs, AppHeader
│       ├── api/                 client.js, watchlistApi.js, liveFeed.js
│       └── utils/               format.js, metrics.js
├── data-generator/
│   ├── generate_market_data.py  synthetic dataset generator
│   ├── load_to_postgres.py      loads output/ into PostgreSQL
│   ├── schema.sql               the actual database schema
│   └── output/                  stocks.json, funds.json, stock_prices.json,
│                                 fund_prices.json, events.json
└── docs/
    └── SCALABILITY.md            source for §12 above
```

### 17. Deployment

The project is deployed end-to-end, not just runnable locally — a live Docker-based deployment on free tiers throughout:

```
Vercel (React/Vite build)
   │
   │  REST (fetch) + SSE (EventSource) — same API contract as local dev, nothing frontend-specific changed for deployment
   ▼
Render (Spring Boot, Docker runtime)
   │
   │  JDBC, pooled connection
   ▼
Neon (PostgreSQL, serverless)
```

**Live URLs:**
- Frontend: https://smart-watchlist-groww.vercel.app/
- Backend: https://smart-watchlist-groww.onrender.com/

**Neon (PostgreSQL)** — the production database. Schema and data were loaded using the exact same, already-existing scripts as local dev (`data-generator/schema.sql`, then `load_to_postgres.py --dsn "<neon-connection-string>"`) — no separate deployment-only tooling was written. Currently seeded with the real dataset: 36 stocks, 15 funds, 4536 `stock_prices` rows, 1890 `fund_navs` rows, 10 `market_events`, one demo user (id=1) with one demo watchlist (id=1, 12 items). Uses Neon's pooled connection endpoint, since the backend's HikariCP pool holds multiple open connections against a free-tier compute budget.

**Render (backend)** — Docker runtime, root directory `backend`, built from `backend/Dockerfile` (see §16). Datasource config is overridden via environment variables (`SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`, pointing at Neon) — Spring's own property-binding picks these up automatically, so no code differs between local and deployed. Free tier: the service sleeps after ~15 minutes idle; the first request afterward takes roughly 30–60 seconds to wake it before responding normally — expected free-tier behavior, not an error.

**Vercel (frontend)** — static Vite build. `VITE_API_BASE_URL` is set to the Render URL above and `VITE_DEMO_USER_ID=1`, both read via `frontend/src/config.js` exactly as they are locally (see `.env.example`) — deploying required no frontend code change, only setting these two build-time env vars.

**PORT handling** — Render assigns its own port at runtime via a `PORT` env var. Rather than touch `application.properties` (unchanged from local dev — see below), this is handled entirely inside `Dockerfile`'s `ENTRYPOINT` (`-Dserver.port=${PORT:-8080}`), falling back to 8080 if `PORT` isn't set, so a plain local `docker run` behaves identically to always.

**CORS** — `WebConfig.java` allows `http://localhost:5173`, `http://localhost:5174`, and `https://smart-watchlist-groww.vercel.app` (see §13). This was the one actual code change deployment required.

**Local vs. deployed — no other differences.** `application.properties` still points at `jdbc:postgresql://localhost:5432/watchlist_db` reading `${DB_PASSWORD}`, exactly as in §18 below — that's correct and intentional, not stale. Local runs use that; the deployed Render environment overrides the same three Spring properties via its own env vars pointing at Neon instead. Same jar, same code, different environment configuration only.

### 18. Running the Project (locally)

This is the local-dev setup — for the live deployed version, see §17.

**Prerequisites:** Java 17, Maven, Node.js, PostgreSQL, Python 3 (for the data generator).

**1. Generate and load the dataset:**
```bash
cd data-generator
pip install -r requirements.txt
python generate_market_data.py
psql -U postgres -d watchlist_db -f schema.sql
python load_to_postgres.py --dsn "postgresql://postgres:<password>@localhost:5432/watchlist_db"
```

**2. Start the backend** (from `backend/`):
```bash
# application.properties reads the DB password from ${DB_PASSWORD}
set DB_PASSWORD=your_postgres_password   # Windows
export DB_PASSWORD=your_postgres_password  # macOS/Linux
mvnw.cmd spring-boot:run     # Windows
./mvnw spring-boot:run       # macOS/Linux
```
Runs on `http://localhost:8080`.

**3. Start the frontend** (from `frontend/`):
```bash
npm install
cp .env.example .env   # adjust VITE_API_BASE_URL if the backend isn't on :8080
npm run dev
```
Opens on `http://localhost:5173`. CORS for this origin is already configured in `WebConfig.java`.

**4. Tests:**
```bash
# backend, from backend/
mvnw.cmd test        # Windows
./mvnw test           # macOS/Linux

# frontend, from frontend/
npm test
npm run build
```

### 19. Future / Production Improvements

- Redis Pub/Sub (or equivalent) for shared tick distribution across multiple backend instances (§12).
- A real market-data provider, if this ever needed to leave "demo" status.
- Proper authentication, replacing the current demo `userId` parameter.
- A persistence strategy for live data if intraday history ever needs to be queried later, rather than only ever reflecting the current in-memory tick.
- Production observability (structured logging, metrics, tracing) around the detection and live-feed paths.

---

*This README reflects the repository as inspected at the time of writing. It describes only functionality that actually exists in the codebase — see the section-by-section references above for exactly where each claim comes from.*
