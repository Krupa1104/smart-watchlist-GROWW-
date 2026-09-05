package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.entity.InstrumentType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the detected_changes fix: previously every call
 * to detect() for a meaningful symbol inserted a NEW row, so simply
 * reloading a watchlist repeatedly (which calls /detect every time) grew
 * this table without bound for data nobody asked to see duplicated. Now
 * persist() dedupes on (symbol, detected_date, changeType), and the table
 * has one genuine read use — countPriorDetections(), surfaced in the
 * instrument detail panel as "flagged N times before".
 */
@SpringBootTest
@Transactional
class DetectedChangeAuditTrailTest {

    @Autowired
    private ChangeDetectionService changeDetectionService;

    @Test
    void repeatedlyDetectingTheSameMeaningfulVerdictDoesNotGrowTheAuditTrail() {
        // STK04 is confirmed organically meaningful on the dataset's latest
        // date (see WatchlistAttentionTest) — calling detect() repeatedly
        // for it, exactly as reloading a watchlist page repeatedly would,
        // must not insert a new detected_changes row every time.
        changeDetectionService.detect("STK04", InstrumentType.STOCK);
        long afterFirst = changeDetectionService.countPriorDetections("STK04");
        assertTrue(afterFirst >= 1, "the first meaningful detection should be recorded at least once");

        for (int i = 0; i < 10; i++) {
            changeDetectionService.detect("STK04", InstrumentType.STOCK);
        }
        long afterMany = changeDetectionService.countPriorDetections("STK04");

        assertEquals(afterFirst, afterMany,
                "detecting the SAME (symbol, date, changeType) repeatedly must not grow the audit trail — "
                        + "got " + afterFirst + " then " + afterMany + " after 10 more identical detections");
    }

    @Test
    void aSymbolNeverFlaggedMeaningfulHasZeroPriorDetections() {
        // STK01 is confirmed quiet on the dataset's latest date (see
        // WatchlistAttentionTest) — detecting it should never write anything.
        changeDetectionService.detect("STK01", InstrumentType.STOCK);
        assertEquals(0, changeDetectionService.countPriorDetections("STK01"));
    }
}
