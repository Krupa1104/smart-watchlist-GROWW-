"""
Loads the synthetic dataset (output/*.json) into PostgreSQL, matching schema.sql.

Run after generate_market_data.py has produced the output/ folder:

    pip install psycopg2-binary
    python load_to_postgres.py --dsn "postgresql://user:pass@localhost:5432/watchlist_db"

A single demo user + watchlist are created and pre-populated with a handful
of instruments so the app has something to show immediately.
"""

import argparse
import json
import os

import psycopg2
from psycopg2.extras import execute_values

OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "output")


def load_json(name):
    with open(os.path.join(OUTPUT_DIR, name)) as f:
        return json.load(f)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dsn", required=True, help="PostgreSQL connection string")
    args = parser.parse_args()

    stocks = load_json("stocks.json")
    funds = load_json("funds.json")
    stock_prices = load_json("stock_prices.json")
    fund_navs = load_json("fund_prices.json")
    events = load_json("events.json")

    conn = psycopg2.connect(args.dsn)
    cur = conn.cursor()

    execute_values(
        cur,
        "INSERT INTO stocks (symbol, name, sector) VALUES %s ON CONFLICT (symbol) DO NOTHING",
        [(s["symbol"], s["name"], s["sector"]) for s in stocks],
    )

    execute_values(
        cur,
        "INSERT INTO funds (symbol, name, category, expense_ratio) VALUES %s ON CONFLICT (symbol) DO NOTHING",
        [(f["symbol"], f["name"], f["category"], f.get("expense_ratio")) for f in funds],
    )

    execute_values(
        cur,
        """INSERT INTO stock_prices (symbol, trade_date, open, high, low, close, volume)
           VALUES %s ON CONFLICT (symbol, trade_date) DO NOTHING""",
        [(r["symbol"], r["date"], r["open"], r["high"], r["low"], r["close"], r["volume"])
         for r in stock_prices],
        page_size=1000,
    )

    execute_values(
        cur,
        "INSERT INTO fund_navs (symbol, nav_date, nav) VALUES %s ON CONFLICT (symbol, nav_date) DO NOTHING",
        [(r["symbol"], r["date"], r["nav"]) for r in fund_navs],
        page_size=1000,
    )

    execute_values(
        cur,
        """INSERT INTO market_events (event_date, scope, symbol, event_type, description)
           VALUES %s""",
        [(e["date"], e["scope"],
          e["symbol"] if e["scope"] != "sector" else None,
          e["type"], e["description"]) for e in events],
    )

    # Seed one demo user + watchlist with a small mixed set of instruments
    cur.execute(
        "INSERT INTO users (name, email) VALUES (%s, %s) RETURNING id",
        ("Demo User", "demo@watchlist.local"),
    )
    user_id = cur.fetchone()[0]

    cur.execute(
        "INSERT INTO watchlists (user_id, name) VALUES (%s, %s) RETURNING id",
        (user_id, "My Watchlist"),
    )
    watchlist_id = cur.fetchone()[0]

    demo_items = [(s["symbol"], "STOCK") for s in stocks[:8]] + \
                 [(f["symbol"], "FUND") for f in funds[:4]]

    execute_values(
        cur,
        "INSERT INTO watchlist_items (watchlist_id, symbol, instrument_type) VALUES %s",
        [(watchlist_id, sym, itype) for sym, itype in demo_items],
    )

    conn.commit()

    cur.execute("SELECT count(*) FROM stock_prices")
    stock_row_count = cur.fetchone()[0]
    cur.execute("SELECT count(*) FROM fund_navs")
    fund_row_count = cur.fetchone()[0]

    print(f"Loaded {len(stocks)} stocks, {len(funds)} funds")
    print(f"Loaded {stock_row_count} stock_prices rows, {fund_row_count} fund_navs rows")
    print(f"Loaded {len(events)} market_events")
    print(f"Seeded demo user (id={user_id}) with watchlist (id={watchlist_id}, {len(demo_items)} items)")

    cur.close()
    conn.close()


if __name__ == "__main__":
    main()
