package com.groww.smart_watchlist.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

// One point of recent history for the instrument detail panel's mini chart
// — value is close price for a STOCK, NAV for a FUND (the same "one field,
// not two" choice MarketDataResponse already makes). volume is null for
// funds (fund_navs has no volume column).
public record PricePointResponse(
        LocalDate date,
        BigDecimal value,
        Long volume
) {
}
