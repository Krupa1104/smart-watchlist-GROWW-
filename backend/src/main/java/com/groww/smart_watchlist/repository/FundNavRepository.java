package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.FundNav;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FundNavRepository extends JpaRepository<FundNav, Long> {

    List<FundNav> findBySymbolOrderByNavDateDesc(String symbol);

    Optional<FundNav> findTopBySymbolOrderByNavDateDesc(String symbol); // latest NAV

    Optional<FundNav> findBySymbolAndNavDate(String symbol, LocalDate navDate);

    // Peer-relative signal per the product thesis: a fund's own NAV z-score
    // isn't the right lens (funds move slower/smoother than stocks) — what
    // matters is how this fund's NAV move compares to its category peers on
    // the same day. With only 3 funds/category in this dataset, a
    // cross-sectional stddev (n=3) is statistically thin, so the caller
    // falls back to a flat percentage-point threshold when the sample is
    // this small — see ChangeDetectionService.
    // Row shape: 0 fund_change, 1 category_avg_change, 2 category_stddev_change,
    // 3 category_sample_size, 4 as_of_date
    @Query(value = """
            WITH fund_changes AS (
                SELECT n.symbol, f.category, n.nav_date,
                       (n.nav - LAG(n.nav) OVER (PARTITION BY n.symbol ORDER BY n.nav_date))
                         / NULLIF(LAG(n.nav) OVER (PARTITION BY n.symbol ORDER BY n.nav_date), 0) AS nav_change
                FROM fund_navs n
                JOIN funds f ON f.symbol = n.symbol
                WHERE f.category = (SELECT category FROM funds WHERE symbol = :symbol)
            ),
            latest AS (
                SELECT * FROM fund_changes
                WHERE nav_date = (SELECT MAX(nav_date) FROM fund_changes WHERE symbol = :symbol)
            )
            SELECT
                (SELECT nav_change FROM latest WHERE symbol = :symbol) AS fund_change,
                AVG(nav_change) AS category_avg_change,
                STDDEV(nav_change) AS category_stddev_change,
                COUNT(nav_change) AS category_sample_size,
                MAX(nav_date) AS as_of_date
            FROM latest
            """, nativeQuery = true)
    List<Object[]> findLatestFundPeerSignal(@Param("symbol") String symbol);

    // Historical counterpart used only by ground-truth tests. Same logic as
    // findLatestFundPeerSignal, but fund_changes is bounded by
    // "nav_date <= :asOfDate" BEFORE the LAG window runs, so each fund's
    // daily change is still computed against its own real prior-day NAV
    // (not a truncated series) — then "latest" is pinned to exactly
    // asOfDate rather than MAX(nav_date).
    @Query(value = """
            WITH fund_changes AS (
                SELECT n.symbol, f.category, n.nav_date,
                       (n.nav - LAG(n.nav) OVER (PARTITION BY n.symbol ORDER BY n.nav_date))
                         / NULLIF(LAG(n.nav) OVER (PARTITION BY n.symbol ORDER BY n.nav_date), 0) AS nav_change
                FROM fund_navs n
                JOIN funds f ON f.symbol = n.symbol
                WHERE f.category = (SELECT category FROM funds WHERE symbol = :symbol)
                  AND n.nav_date <= :asOfDate
            ),
            latest AS (
                SELECT * FROM fund_changes
                WHERE nav_date = :asOfDate
            )
            SELECT
                (SELECT nav_change FROM latest WHERE symbol = :symbol) AS fund_change,
                AVG(nav_change) AS category_avg_change,
                STDDEV(nav_change) AS category_stddev_change,
                COUNT(nav_change) AS category_sample_size,
                MAX(nav_date) AS as_of_date
            FROM latest
            """, nativeQuery = true)
    List<Object[]> findFundPeerSignalAsOf(@Param("symbol") String symbol, @Param("asOfDate") LocalDate asOfDate);
}

