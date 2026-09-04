package com.groww.smart_watchlist.repository;

import com.groww.smart_watchlist.entity.Fund;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundRepository extends JpaRepository<Fund, String> {
}
