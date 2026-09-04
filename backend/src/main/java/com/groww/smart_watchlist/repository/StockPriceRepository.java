package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    List<StockPrice> findBySymbolOrderByTradeDateDesc(String symbol);

    Optional<StockPrice> findTopBySymbolOrderByTradeDateDesc(String symbol); // latest price

    Optional<StockPrice> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);

    // Same rolling-window approach validated manually against the loaded
    // dataset (see schema.sql's example query) — z-score of today's return
    // vs the stock's own trailing-20-day mean/stddev, plus today's volume
    // vs its own trailing-20-day average volume, in a single round trip.
    // Row shape (indexes into the Object[]):
    // 0 trade_date, 1 close, 2 daily_return, 3 avg_return_20d,
    // 4 stddev_return_20d, 5 return_z_score, 6 volume, 7 avg_volume_20d,
    // 8 volume_ratio
    @Query(value = """
            WITH price_stats AS (
                SELECT trade_date, close, volume,
                       (close - LAG(close) OVER (ORDER BY trade_date))
                         / NULLIF(LAG(close) OVER (ORDER BY trade_date), 0) AS daily_return
                FROM stock_prices
                WHERE symbol = :symbol
            ),
            rolling AS (
                SELECT trade_date, close, volume, daily_return,
                       AVG(daily_return) OVER (ORDER BY trade_date
                           ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING) AS avg_return_20d,
                       STDDEV(daily_return) OVER (ORDER BY trade_date
                           ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING) AS stddev_return_20d,
                       AVG(volume) OVER (ORDER BY trade_date
                           ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING) AS avg_volume_20d
                FROM price_stats
            )
            SELECT trade_date, close, daily_return, avg_return_20d, stddev_return_20d,
                   (daily_return - avg_return_20d) / NULLIF(stddev_return_20d, 0) AS return_z_score,
                   volume, avg_volume_20d,
                   volume / NULLIF(avg_volume_20d, 0) AS volume_ratio
            FROM rolling
            ORDER BY trade_date DESC
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findLatestStockSignal(@Param("symbol") String symbol);

    // Historical counterpart used only by ground-truth tests (ChangeDetectionService.evaluateAsOf).
    // Same rolling-window logic as findLatestStockSignal, but the CTE is
    // bounded by "trade_date <= :asOfDate" BEFORE the window functions run,
    // so AVG/STDDEV/LAG still see the full run of trading days up to and
    // including asOfDate (i.e. the correct 20 rows preceding it) — not a
    // narrower window sliced out after the fact, which would starve the
    // rolling stats of real history. The final SELECT then pins to exactly
    // asOfDate instead of "whatever's latest".
    @Query(value = """
            WITH price_stats AS (
                SELECT trade_date, close, volume,
                       (close - LAG(close) OVER (ORDER BY trade_date))
                         / NULLIF(LAG(close) OVER (ORDER BY trade_date), 0) AS daily_return
                FROM stock_prices
                WHERE symbol = :symbol
                  AND trade_date <= :asOfDate
            ),
            rolling AS (
                SELECT trade_date, close, volume, daily_return,
                       AVG(daily_return) OVER (ORDER BY trade_date
                           ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING) AS avg_return_20d,
                       STDDEV(daily_return) OVER (ORDER BY trade_date
                           ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING) AS stddev_return_20d,
                       AVG(volume) OVER (ORDER BY trade_date
                           ROWS BETWEEN 20 PRECEDING AND 1 PRECEDING) AS avg_volume_20d
                FROM price_stats
            )
            SELECT trade_date, close, daily_return, avg_return_20d, stddev_return_20d,
                   (daily_return - avg_return_20d) / NULLIF(stddev_return_20d, 0) AS return_z_score,
                   volume, avg_volume_20d,
                   volume / NULLIF(avg_volume_20d, 0) AS volume_ratio
            FROM rolling
            WHERE trade_date = :asOfDate
            """, nativeQuery = true)
    List<Object[]> findStockSignalAsOf(@Param("symbol") String symbol, @Param("asOfDate") LocalDate asOfDate);
}

