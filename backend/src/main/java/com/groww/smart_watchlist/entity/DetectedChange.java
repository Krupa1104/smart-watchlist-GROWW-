package com.groww.smart_watchlist.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "detected_changes")
@Getter
@Setter
public class DetectedChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(nullable = false, length = 20)
    private String symbol;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "instrument_type", columnDefinition = "instrument_type", nullable = false)
    private InstrumentType instrumentType;

    @Column(name = "detected_date", nullable = false)
    private LocalDate detectedDate;

    @Column(name = "change_type", nullable = false, length = 50)
    private String changeType;

    @Column(name = "severity_score", nullable = false, precision = 6, scale = 3)
    private BigDecimal severityScore;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String explanation;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private OffsetDateTime createdAt;
}