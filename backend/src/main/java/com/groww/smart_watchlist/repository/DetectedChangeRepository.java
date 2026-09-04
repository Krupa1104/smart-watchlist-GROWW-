package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.DetectedChange;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface DetectedChangeRepository extends JpaRepository<DetectedChange, Integer> {
    List<DetectedChange> findBySymbolOrderByDetectedDateDesc(String symbol);
}
