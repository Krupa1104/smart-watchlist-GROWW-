# Smart Watchlist — Frontend (Groww-style redesign)

React + Vite dashboard for the Smart Market Watchlist backend. Talks only to
the existing Spring Boot APIs — no data is fabricated client-side.

This is a visual/IA redesign of the working frontend, restructured to feel
like a familiar investment-platform watchlist (inspired by Groww's layout,
not its branding) while keeping the "What changed" attention layer as the
clear differentiator.

## Setup

```
cd frontend
npm install
cp .env.example .env   # adjust VITE_API_BASE_URL if your backend isn't on :8080
```

## Run

```
npm run dev
```

Opens at `http://localhost:5173`. The backend must be running at the URL in
`.env` (default `http://localhost:8080`) with the `watchlist_db` database
populated (see `data-generator/README.md`), and must allow CORS from this
origin (already configured in the backend's `WebConfig.java`).

## Test

```
npm test
```

## Build

```
npm run build
```

## Layout

- `AppHeader` — top nav (brand, Stocks/Mutual Funds/Watchlist, global search, profile)
- `WatchlistTabs` — real multi-watchlist switcher (`listWatchlists`/`createWatchlist`)
- `AttentionSection` / `AttentionCard` — the "What changed" digest (`/attention`)
- `WatchlistToolbar` — in-watchlist search, Add/Edit toggles, Check for changes
- `WatchlistTable` — dense instrument table, enriched with real `/detect` metrics (1D change, volume) for every row, not just flagged ones
- `AddItemForm` — collapsible add-instrument form

No market index ticker (NIFTY/SENSEX/etc.), sparklines, or 52-week range are
shown — the backend has no index-data or historical-series endpoint, and
inventing values would violate the "don't fabricate market data" rule.

## Notes

- `src/config.js` is the only place reading environment variables.
- `src/api/` is the only place calling `fetch` — everything else goes through it.
- `src/utils/metrics.js` reads the real metric keys from `ChangeDetectionService.java`
  (`dailyReturn`, `fundChangePct`, `volumeRatio`) rather than guessing field names.
- `POST /check` is only ever called from the "Check for changes" button
  (`WatchlistToolbar`) — never on page load or a plain refresh.
