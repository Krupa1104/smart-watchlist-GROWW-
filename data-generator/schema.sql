-- ============================================================================
-- Smart Market Watchlist — PostgreSQL schema
-- ============================================================================
-- Design notes:
--  - stocks/funds kept as separate tables (different attributes: sector vs
--    category) but watchlist_items references either via a symbol + type,
--    so the watchlist layer doesn't care which kind of instrument it is.
--  - watchlist_snapshots is the key table for "what changed since you last
--    checked" — it stores the price/NAV the user last SAW, per item, so a
--    diff is just "current value vs snapshot value", not a live stream.
--  - market_events holds the ground-truth injected events (from events.json)
--    — useful for testing your detection logic against a known answer key.
--  - detected_changes is DIFFERENT: it's where YOUR engine writes its own
--    output (what it decided was meaningful and why). Keeping this separate
--    from market_events gives you an audit trail: "here's the raw event,
--    here's what our system inferred from the data independently."
-- ============================================================================

CREATE TABLE users (
    id            SERIAL PRIMARY KEY,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(255) UNIQUE NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stocks (
    symbol        VARCHAR(20) PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    sector        VARCHAR(50) NOT NULL
);

CREATE TABLE funds (
    symbol          VARCHAR(20) PRIMARY KEY,
    name            VARCHAR(150) NOT NULL,
    category        VARCHAR(50) NOT NULL,
    expense_ratio   NUMERIC(5,2)
);

-- Time-series price data. One row per (symbol, date).
CREATE TABLE stock_prices (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20) NOT NULL REFERENCES stocks(symbol),
    trade_date  DATE NOT NULL,
    open        NUMERIC(12,2) NOT NULL,
    high        NUMERIC(12,2) NOT NULL,
    low         NUMERIC(12,2) NOT NULL,
    close       NUMERIC(12,2) NOT NULL,
    volume      BIGINT NOT NULL,
    UNIQUE (symbol, trade_date)
);
CREATE INDEX idx_stock_prices_symbol_date ON stock_prices (symbol, trade_date DESC);

CREATE TABLE fund_navs (
    id          BIGSERIAL PRIMARY KEY,
    symbol      VARCHAR(20) NOT NULL REFERENCES funds(symbol),
    nav_date    DATE NOT NULL,
    nav         NUMERIC(12,4) NOT NULL,
    UNIQUE (symbol, nav_date)
);
CREATE INDEX idx_fund_navs_symbol_date ON fund_navs (symbol, nav_date DESC);

-- A user's watchlist. Kept separate from items so a user could have >1 list
-- later (e.g. "Core" vs "Watching") without a schema change.
CREATE TABLE watchlists (
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER NOT NULL REFERENCES users(id),
    name        VARCHAR(100) NOT NULL DEFAULT 'My Watchlist',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TYPE instrument_type AS ENUM ('STOCK', 'FUND');

CREATE TABLE watchlist_items (
    id               SERIAL PRIMARY KEY,
    watchlist_id     INTEGER NOT NULL REFERENCES watchlists(id) ON DELETE CASCADE,
    symbol           VARCHAR(20) NOT NULL,
    instrument_type  instrument_type NOT NULL,
    added_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (watchlist_id, symbol)
);

-- The core "since you last checked" mechanism: what value did the user
-- last SEE for this item, and when. Updated every time they open the app.
CREATE TABLE watchlist_snapshots (
    id                  SERIAL PRIMARY KEY,
    watchlist_item_id   INTEGER NOT NULL REFERENCES watchlist_items(id) ON DELETE CASCADE,
    last_viewed_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_value     NUMERIC(12,4) NOT NULL,   -- price or NAV at last view
    UNIQUE (watchlist_item_id)
);

-- Ground-truth events baked into the synthetic dataset (from events.json).
CREATE TABLE market_events (
    id            SERIAL PRIMARY KEY,
    event_date    DATE NOT NULL,
    scope         VARCHAR(20) NOT NULL,   -- 'stock' | 'sector' | 'fund'
    symbol        VARCHAR(20),            -- null when scope = 'sector'
    event_type    VARCHAR(50) NOT NULL,
    description   TEXT NOT NULL
);

-- Output of YOUR detection engine — this is the "smart" layer's memory.
CREATE TABLE detected_changes (
    id                SERIAL PRIMARY KEY,
    symbol            VARCHAR(20) NOT NULL,
    instrument_type   instrument_type NOT NULL,
    detected_date     DATE NOT NULL,
    change_type       VARCHAR(50) NOT NULL,   -- e.g. 'volatility_spike', '52w_high', 'category_underperform'
    severity_score     NUMERIC(6,3) NOT NULL,  -- your ranking/attention score
    explanation       TEXT NOT NULL,           -- plain-language reason, shown in the digest
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_detected_changes_symbol_date ON detected_changes (symbol, detected_date DESC);

-- ============================================================================
-- Example: the kind of query this schema is built to make easy.
-- Rolling 20-day volatility + z-score of today's move vs a stock's own
-- history — this is the heart of "meaningful relative to itself", using
-- native window functions instead of pulling data into app code to compute.
-- ============================================================================
-- WITH daily_returns AS (
--     SELECT symbol, trade_date, close,
--            (close - LAG(close) OVER (PARTITION BY symbol ORDER BY trade_date))
--              / LAG(close) OVER (PARTITION BY symbol ORDER BY trade_date) AS daily_return
--     FROM stock_prices
-- ),
-- rolling_stats AS (
--     SELECT *,
--            AVG(daily_return) OVER (PARTITION BY symbol ORDER BY trade_date
--                ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING) AS avg_return_20d,
--            STDDEV(daily_return) OVER (PARTITION BY symbol ORDER BY trade_date
--                ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING) AS stddev_return_20d
--     FROM daily_returns
-- )
-- SELECT symbol, trade_date, daily_return,
--        (daily_return - avg_return_20d) / NULLIF(stddev_return_20d, 0) AS z_score
-- FROM rolling_stats
-- WHERE trade_date = CURRENT_DATE
-- ORDER BY ABS((daily_return - avg_return_20d) / NULLIF(stddev_return_20d, 0)) DESC;
