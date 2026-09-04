package com.groww.smart_watchlist.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

// Plain "symbol" string here rather than a @ManyToOne to Stock — the
// detection logic will mostly run aggregate SQL/JPQL over price history,
// not navigate the object graph, so a full relationship just adds N+1 risk
// for no benefit.
@Entity
@Table(name = "stock_prices", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "trade_date"}))
@Getter
@Setter
public class StockPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal open;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal high;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal low;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal close;

    @Column(nullable = false)
    private Long volume;
}