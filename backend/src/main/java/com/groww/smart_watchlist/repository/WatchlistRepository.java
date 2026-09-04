package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.Watchlist;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WatchlistRepository extends JpaRepository<Watchlist, Integer> {
    List<Watchlist> findByUserId(Integer userId);
}
