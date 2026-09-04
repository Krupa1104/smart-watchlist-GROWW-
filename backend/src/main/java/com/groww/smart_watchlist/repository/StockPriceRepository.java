package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.StockPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface StockPriceRepository extends JpaRepository<StockPrice, Long> {

    List<StockPrice> findBySymbolOrderByTradeDateDesc(String symbol);

    Optional<StockPrice> findTopBySymbolOrderByTradeDateDesc(String symbol); // latest price

    Optional<StockPrice> findBySymbolAndTradeDate(String symbol, LocalDate tradeDate);
}
