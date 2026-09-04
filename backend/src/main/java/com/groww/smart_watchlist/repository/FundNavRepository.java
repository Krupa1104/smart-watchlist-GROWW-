package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.FundNav;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface FundNavRepository extends JpaRepository<FundNav, Long> {

    List<FundNav> findBySymbolOrderByNavDateDesc(String symbol);

    Optional<FundNav> findTopBySymbolOrderByNavDateDesc(String symbol); // latest NAV

    Optional<FundNav> findBySymbolAndNavDate(String symbol, LocalDate navDate);
}
