package com.groww.smart_watchlist.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.OffsetDateTime;

@Entity
@Table(name = "watchlist_items", uniqueConstraints = @UniqueConstraint(columnNames = {"watchlist_id", "symbol"}))
@Getter
@Setter
public class WatchlistItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "watchlist_id", nullable = false)
    private Watchlist watchlist;

    @Column(nullable = false, length = 20)
    private String symbol;

    // instrument_type is a native Postgres ENUM. NAMED_ENUM tells Hibernate
    // to map the Java enum's name directly onto that Postgres type by name.
    // Requires Hibernate 6.2+ (bundled with Spring Boot 3.2+) — if your
    // Initializr version is older than that, say so and I'll give you the
    // VARCHAR + check-constraint fallback instead.
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "instrument_type", columnDefinition = "instrument_type", nullable = false)
    private InstrumentType instrumentType;

    @CreationTimestamp
    @Column(name = "added_at", updatable = false)
    private OffsetDateTime addedAt;
}