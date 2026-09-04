"""
Synthetic market dataset generator for the "Smart Market Watchlist" hackathon build.

Generates ~6 months of daily OHLCV data for a mid-size universe of stocks and
mutual funds, with deliberately injected "storyline" events (earnings shocks,
sector rallies, manager changes, etc.) so the detection engine you build on top
of this can be tested against KNOWN ground truth instead of hoping something
interesting happens to show up.

Run:
    pip install numpy
    python generate_market_data.py

Outputs (in ./output/):
    stocks.json        - stock metadata (symbol, name, sector)
    stock_prices.json  - daily OHLCV rows for every stock
    funds.json          - fund metadata (symbol, name, category)
    fund_prices.json   - daily NAV rows for every fund
    events.json        - ground-truth log of every injected storyline event
"""

import json
import os
import random
from datetime import date, timedelta

import numpy as np

# ---------------------------------------------------------------------------
# CONFIG
# ---------------------------------------------------------------------------
SEED = 42
NUM_DAYS = 126              # ~6 months of trading days
START_DATE = date(2026, 3, 2)
OUTPUT_DIR = os.path.join(os.path.dirname(__file__), "output")

random.seed(SEED)
np.random.seed(SEED)

SECTORS = ["Banking", "IT", "Pharma", "Auto", "Energy", "FMCG"]
STOCKS_PER_SECTOR = 6        # 6 sectors x 6 = 36 stocks

FUND_CATEGORIES = ["Large Cap", "Mid Cap", "Small Cap", "Debt", "Hybrid"]
FUNDS_PER_CATEGORY = 3        # 5 categories x 3 = 15 funds
# Total universe: 36 + 15 = 51 instruments (medium-sized, per plan)

NAME_POOL = [
    "Alpha", "Zenith", "Vertex", "Orion", "Nova", "Sterling", "Everest",
    "Pioneer", "Horizon", "Summit", "Meridian", "Crest", "Atlas", "Vantage",
    "Beacon", "Catalyst", "Cascade", "Ember", "Falcon", "Granite", "Harbor",
    "Ignite", "Juniper", "Kinetic", "Luminous", "Monarch", "Nimbus", "Onyx",
    "Prism", "Quantum", "Radiant", "Solstice", "Tandem", "Unity", "Vector",
    "Wavelength", "Axiom", "Bastion", "Cobalt", "Delta", "Echo", "Fusion",
    "Genesis", "Helix", "Indigo", "Jasper", "Keystone", "Legacy", "Momentum",
    "Nexus", "Odyssey",
]

# ---------------------------------------------------------------------------
# UNIVERSE GENERATION
# ---------------------------------------------------------------------------

def build_stocks():
    stocks = []
    name_iter = iter(NAME_POOL)
    idx = 1
    for sector in SECTORS:
        # sector-level volatility & drift characteristics differ a bit
        sector_base_vol = {
            "Banking": 0.014, "IT": 0.016, "Pharma": 0.018,
            "Auto": 0.017, "Energy": 0.020, "FMCG": 0.011,
        }[sector]
        for _ in range(STOCKS_PER_SECTOR):
            symbol = f"STK{idx:02d}"
            name = f"{next(name_iter)} {sector}"
            stocks.append({
                "symbol": symbol,
                "name": name,
                "sector": sector,
                "listing_price": round(random.uniform(150, 3500), 2),
                "daily_vol": round(sector_base_vol * random.uniform(0.75, 1.35), 5),
                "drift": round(random.uniform(-0.0003, 0.0007), 6),
                "sector_beta": round(random.uniform(0.5, 1.3), 2),
            })
            idx += 1
    return stocks


def build_funds():
    funds = []
    name_iter = iter(NAME_POOL[::-1])
    idx = 1
    category_vol = {
        "Large Cap": 0.006, "Mid Cap": 0.009, "Small Cap": 0.013,
        "Debt": 0.0015, "Hybrid": 0.005,
    }
    for category in FUND_CATEGORIES:
        for _ in range(FUNDS_PER_CATEGORY):
            symbol = f"FUND{idx:02d}"
            name = f"{next(name_iter)} {category} Fund"
            funds.append({
                "symbol": symbol,
                "name": name,
                "category": category,
                "base_nav": round(random.uniform(20, 450), 2),
                "daily_vol": round(category_vol[category] * random.uniform(0.8, 1.3), 6),
                "drift": round(random.uniform(-0.0001, 0.0004), 6),
                "category_beta": round(random.uniform(0.6, 1.1), 2),
                "expense_ratio": round(random.uniform(0.4, 2.1), 2),
            })
            idx += 1
    return funds


# ---------------------------------------------------------------------------
# STORYLINE EVENTS (ground truth - injected deliberately, not random noise)
# ---------------------------------------------------------------------------

def build_events(stocks, funds):
    """Hand-placed events so the detection engine has known signal to find."""
    events = [
        {"day": 20, "symbol": stocks[6]["symbol"], "scope": "stock",
         "type": "earnings_beat", "price_shock": 0.09, "volume_mult": 3.5,
         "description": f"{stocks[6]['name']} beats Q1 earnings estimates"},

        {"day": 34, "symbol": stocks[13]["symbol"], "scope": "stock",
         "type": "earnings_miss", "price_shock": -0.11, "volume_mult": 4.0,
         "description": f"{stocks[13]['name']} misses Q1 estimates, cuts guidance"},

        {"day": 48, "symbol": stocks[2]["symbol"], "scope": "stock",
         "type": "regulatory_action", "price_shock": -0.15, "volume_mult": 5.0,
         "description": f"Regulator fines {stocks[2]['name']} over compliance lapse"},

        {"day": 60, "symbol": "IT", "scope": "sector",
         "type": "sector_rally", "price_shock": 0.06, "volume_mult": 2.2,
         "description": "IT sector rallies on strong global tech demand outlook"},

        {"day": 72, "symbol": stocks[27]["symbol"], "scope": "stock",
         "type": "insider_buying", "price_shock": 0.05, "volume_mult": 2.6,
         "description": f"Promoter increases stake in {stocks[27]['name']}"},

        {"day": 85, "symbol": "Energy", "scope": "sector",
         "type": "sector_selloff", "price_shock": -0.07, "volume_mult": 2.8,
         "description": "Energy sector slides on falling crude oil prices"},

        {"day": 95, "symbol": stocks[31]["symbol"], "scope": "stock",
         "type": "product_launch", "price_shock": 0.07, "volume_mult": 2.4,
         "description": f"{stocks[31]['name']} unveils new flagship product line"},

        {"day": 40, "symbol": funds[3]["symbol"], "scope": "fund",
         "type": "manager_change", "price_shock": 0.0, "volume_mult": 1.0,
         "description": f"Fund manager transition announced for {funds[3]['name']}"},

        {"day": 105, "symbol": funds[9]["symbol"], "scope": "fund",
         "type": "rating_upgrade", "price_shock": 0.0, "volume_mult": 1.0,
         "description": f"{funds[9]['name']} upgraded to 5-star rating by ratings agency"},

        {"day": 110, "symbol": stocks[18]["symbol"], "scope": "stock",
         "type": "guidance_cut", "price_shock": -0.08, "volume_mult": 3.0,
         "description": f"{stocks[18]['name']} lowers full-year revenue guidance"},
    ]
    return events


# ---------------------------------------------------------------------------
# PRICE SIMULATION
# ---------------------------------------------------------------------------

def trading_dates(num_days, start):
    """Skip weekends to look like real trading calendar."""
    dates = []
    d = start
    while len(dates) < num_days:
        if d.weekday() < 5:
            dates.append(d)
        d += timedelta(days=1)
    return dates


def simulate_sector_factors(num_days):
    """Shared daily return per sector so same-sector stocks move together."""
    factors = {s: np.random.normal(0, 0.006, num_days) for s in SECTORS}
    return factors


def simulate_category_factors(num_days):
    factors = {c: np.random.normal(0, 0.004, num_days) for c in FUND_CATEGORIES}
    return factors


def apply_events_to_factor(factors, events, scope_key, num_days):
    """Bump a sector/category factor series on its event day."""
    for ev in events:
        if ev["scope"] == scope_key.rstrip("s") and ev["symbol"] in factors:
            factors[ev["symbol"]][ev["day"]] += ev["price_shock"]
    return factors


def simulate_stock_prices(stocks, events, sector_factors, dates):
    rows = []
    events_by_symbol = {}
    for ev in events:
        if ev["scope"] == "stock":
            events_by_symbol.setdefault(ev["symbol"], []).append(ev)

    for stock in stocks:
        price = stock["listing_price"]
        base_volume = random.randint(200_000, 2_000_000)
        sym_events = {e["day"]: e for e in events_by_symbol.get(stock["symbol"], [])}

        for day_idx, d in enumerate(dates):
            noise = np.random.normal(0, stock["daily_vol"])
            sector_component = stock["sector_beta"] * sector_factors[stock["sector"]][day_idx]
            ret = stock["drift"] + sector_component + noise

            volume_mult = 1.0
            if day_idx in sym_events:
                ret += sym_events[day_idx]["price_shock"]
                volume_mult = sym_events[day_idx]["volume_mult"]

            prev_close = price
            close = round(prev_close * (1 + ret), 2)
            gap = np.random.normal(0, stock["daily_vol"] / 3)
            open_ = round(prev_close * (1 + gap), 2)
            spread = abs(close - open_) + prev_close * stock["daily_vol"] * random.uniform(0.5, 1.8)
            high = round(max(open_, close) + spread * random.uniform(0, 0.6), 2)
            low = round(min(open_, close) - spread * random.uniform(0, 0.6), 2)
            volume = int(base_volume * volume_mult * np.random.lognormal(0, 0.25))

            rows.append({
                "symbol": stock["symbol"],
                "date": d.isoformat(),
                "open": open_,
                "high": high,
                "low": low,
                "close": close,
                "volume": volume,
            })
            price = close
    return rows


def simulate_fund_navs(funds, events, category_factors, dates):
    rows = []
    events_by_symbol = {}
    for ev in events:
        if ev["scope"] == "fund":
            events_by_symbol.setdefault(ev["symbol"], []).append(ev)

    for fund in funds:
        nav = fund["base_nav"]
        sym_events = {e["day"]: e for e in events_by_symbol.get(fund["symbol"], [])}

        for day_idx, d in enumerate(dates):
            noise = np.random.normal(0, fund["daily_vol"])
            category_component = fund["category_beta"] * category_factors[fund["category"]][day_idx]
            ret = fund["drift"] + category_component + noise

            if day_idx in sym_events:
                ret += sym_events[day_idx]["price_shock"]  # usually 0 for funds (e.g. manager change)

            nav = round(nav * (1 + ret), 4)
            rows.append({
                "symbol": fund["symbol"],
                "date": d.isoformat(),
                "nav": nav,
            })
    return rows


# ---------------------------------------------------------------------------
# MAIN
# ---------------------------------------------------------------------------

def main():
    os.makedirs(OUTPUT_DIR, exist_ok=True)

    stocks = build_stocks()
    funds = build_funds()
    events = build_events(stocks, funds)
    dates = trading_dates(NUM_DAYS, START_DATE)

    sector_factors = simulate_sector_factors(NUM_DAYS)
    category_factors = simulate_category_factors(NUM_DAYS)
    sector_factors = apply_events_to_factor(sector_factors, events, "sectors", NUM_DAYS)
    category_factors = apply_events_to_factor(category_factors, events, "categorys", NUM_DAYS)

    stock_prices = simulate_stock_prices(stocks, events, sector_factors, dates)
    fund_navs = simulate_fund_navs(funds, events, category_factors, dates)

    events_out = [
        {**ev, "date": dates[ev["day"]].isoformat()} for ev in events
    ]

    with open(os.path.join(OUTPUT_DIR, "stocks.json"), "w") as f:
        json.dump(stocks, f, indent=2)
    with open(os.path.join(OUTPUT_DIR, "funds.json"), "w") as f:
        json.dump(funds, f, indent=2)
    with open(os.path.join(OUTPUT_DIR, "stock_prices.json"), "w") as f:
        json.dump(stock_prices, f, indent=2)
    with open(os.path.join(OUTPUT_DIR, "fund_prices.json"), "w") as f:
        json.dump(fund_navs, f, indent=2)
    with open(os.path.join(OUTPUT_DIR, "events.json"), "w") as f:
        json.dump(events_out, f, indent=2)

    print(f"Generated {len(stocks)} stocks, {len(funds)} funds, "
          f"{len(dates)} trading days ({dates[0]} to {dates[-1]})")
    print(f"{len(stock_prices)} stock price rows, {len(fund_navs)} fund NAV rows")
    print(f"{len(events_out)} ground-truth storyline events injected")
    print(f"Output written to: {OUTPUT_DIR}/")


if __name__ == "__main__":
    main()
