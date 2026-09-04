package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockRepository extends JpaRepository<Stock, String> {
    // symbol is already the ID, so findAll()/findById() cover most needs
}
