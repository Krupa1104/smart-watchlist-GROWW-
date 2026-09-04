package com.groww.smart_watchlist.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

// This table IS the "since you last checked" mechanism — last_viewed_at
// and last_seen_value get overwritten every time the user opens the app,
// so @UpdateTimestamp (not @CreationTimestamp) is deliberate here.
@Entity
@Table(name = "watchlist_snapshots")
@Getter
@Setter
public class WatchlistSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_item_id", nullable = false, unique = true)
    private WatchlistItem watchlistItem;

    @UpdateTimestamp
    @Column(name = "last_viewed_at", nullable = false)
    private OffsetDateTime lastViewedAt;

    @Column(name = "last_seen_value", nullable = false, precision = 12, scale = 4)
    private BigDecimal lastSeenValue;
}