package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.MarketEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface MarketEventRepository extends JpaRepository<MarketEvent, Integer> {
    List<MarketEvent> findBySymbol(String symbol);
}
