package com.groww.smart_watchlist.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "fund_navs", uniqueConstraints = @UniqueConstraint(columnNames = {"symbol", "nav_date"}))
@Getter
@Setter
public class FundNav {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Column(name = "nav_date", nullable = false)
    private LocalDate navDate;

    @Column(nullable = false, precision = 12, scale = 4)
    private BigDecimal nav;
}