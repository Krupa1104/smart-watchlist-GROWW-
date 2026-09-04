# Synthetic Market Dataset — Smart Watchlist

Generates a controlled, reproducible market dataset so your detection/"what
changed" engine can be built and demoed against **known ground truth**,
instead of hoping real market data does something interesting during your
72-hour window.

## Universe
- **36 stocks** across 6 sectors (Banking, IT, Pharma, Auto, Energy, FMCG) — 6 per sector
- **15 mutual funds** across 5 categories (Large Cap, Mid Cap, Small Cap, Debt, Hybrid) — 3 per category
- **126 trading days** (~6 months, weekends skipped), starting 2026-03-02

## How the data is built
- Each stock has its own volatility, drift, and a "sector beta" — stocks in
  the same sector share a common daily sector factor, so sector-wide moves
  emerge naturally (useful for your "did this move because of the sector, or
  on its own?" logic).
- Each fund similarly shares a category factor with peers in the same
  category — this is what lets you build category-relative comparisons
  (e.g. "this fund fell behind its category average for the first time").
- **10 storyline events are deliberately injected** (earnings beats/misses,
  regulatory action, sector rally/selloff, manager change, rating upgrade,
  guidance cut) at specific days for specific symbols. These are logged in
  `events.json` with the real reason — so you can verify your engine
  actually detects and correctly explains these moments, not just flags
  random noise.
- Daily bars are OHLCV (open/high/low/close/volume) rather than tick-level —
  enough texture for gaps, ranges and volume-spike detection without the
  overhead of simulating live ticks for 51 instruments over 6 months. Funds
  only get a daily NAV (real mutual funds don't trade intraday either).

## Run it
```bash
pip install -r requirements.txt
python generate_market_data.py
```

## Output (`output/`)
| File | Contents |
|---|---|
| `stocks.json` | symbol, name, sector, listing price, volatility params |
| `stock_prices.json` | daily OHLCV rows, one per (stock, date) |
| `funds.json` | symbol, name, category, base NAV, expense ratio |
| `fund_prices.json` | daily NAV rows, one per (fund, date) |
| `events.json` | ground-truth log of every injected event, with date + plain-language reason |

## Suggested next step
Load these JSON files into whatever DB you're using for the backend
(SQLite is fine to start, Postgres if you want it deploy-ready sooner).
Treat `events.json` as your answer key while building the "meaningful
change" detection logic — if your engine flags the same dates/symbols with
a similar reason, you know it's working.

## Re-running with different parameters
Everything is controlled at the top of `generate_market_data.py`
(`SEED`, `NUM_DAYS`, `START_DATE`, sector/category counts). Change the
`SEED` for a different random universe, or extend `NUM_DAYS` if you want
more history — the rest of the script adapts automatically.
