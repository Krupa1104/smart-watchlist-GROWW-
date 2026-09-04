package com.groww.smart_watchlist.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;

@Entity
@Table(name = "funds")
@Getter
@Setter
public class Fund {

    @Id
    @Column(length = 20)
    private String symbol;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String category;

    @Column(name = "expense_ratio", precision = 5, scale = 2)
    private BigDecimal expenseRatio;
}