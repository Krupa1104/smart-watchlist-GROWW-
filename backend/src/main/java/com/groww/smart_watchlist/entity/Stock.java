package com.groww.smart_watchlist.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "stocks")
@Getter
@Setter
public class Stock {

    @Id
    @Column(length = 20)
    private String symbol;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 50)
    private String sector;
}