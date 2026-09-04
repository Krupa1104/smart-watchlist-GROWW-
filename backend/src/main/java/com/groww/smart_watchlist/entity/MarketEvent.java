package com.groww.smart_watchlist.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Entity
@Table(name = "market_events")
@Getter
@Setter
public class MarketEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "event_date", nullable = false)
    private LocalDate eventDate;

    @Column(nullable = false, length = 20)
    private String scope; // "stock" | "sector" | "fund"

    @Column(length = 20)
    private String symbol; // null when scope = "sector"

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;
}