package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.DetectedChangeResponse;
import com.groww.smart_watchlist.dto.RelatedEventResponse;
import com.groww.smart_watchlist.entity.InstrumentType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SuggestionServiceTest {

    private final SuggestionService suggestionService = new SuggestionService();

    // Every non-prescriptive word this feature must never emit, per the
    // hackathon brief's explicit rule. Checked case-insensitively against
    // every suggestion this service can ever produce.
    private static final List<String> FORBIDDEN_WORDS =
            List.of("buy", "sell", "invest", "guarantee", "will rise", "will fall");

    @Test
    void returnsNoSuggestionsForANonMeaningfulChange() {
        DetectedChangeResponse normal = new DetectedChangeResponse(
                "STK01", InstrumentType.STOCK, LocalDate.of(2026, 8, 24),
                false, null, null, "Moved within its normal range.", Map.of());

        List<String> suggestions = suggestionService.suggestActions(normal, Optional.empty());

        assertTrue(suggestions.isEmpty(), "a normal move should have nothing to suggest");
    }

    @Test
    void suggestsReviewingTheEventWhenOneIsRelated() {
        DetectedChangeResponse meaningful = meaningfulChange(Map.of("returnZScore", BigDecimal.valueOf(2.5)));
        RelatedEventResponse event = new RelatedEventResponse(
                LocalDate.of(2026, 3, 30), "stock", "earnings_beat", "Beats Q1 estimates");

        List<String> suggestions = suggestionService.suggestActions(meaningful, Optional.of(event));

        assertTrue(suggestions.stream().anyMatch(s -> s.toLowerCase().contains("related event")),
                () -> "expected an event-review suggestion; got: " + suggestions);
    }

    @Test
    void suggestsTreatingItAsStatisticalOnlyWhenNoEventIsRelated() {
        DetectedChangeResponse meaningful = meaningfulChange(Map.of("returnZScore", BigDecimal.valueOf(2.5)));

        List<String> suggestions = suggestionService.suggestActions(meaningful, Optional.empty());

        assertTrue(suggestions.stream().anyMatch(s -> s.toLowerCase().contains("no recorded event")),
                () -> "expected a 'no recorded event' suggestion; got: " + suggestions);
    }

    @Test
    void addsAVolumeSpecificSuggestionWhenVolumeRatioIsHigh() {
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("returnZScore", BigDecimal.valueOf(2.1));
        metrics.put("volumeRatio", BigDecimal.valueOf(3.2)); // well above the 2.0x threshold
        DetectedChangeResponse meaningful = meaningfulChange(metrics);

        List<String> suggestions = suggestionService.suggestActions(meaningful, Optional.empty());

        assertTrue(suggestions.stream().anyMatch(s -> s.toLowerCase().contains("volume")),
                () -> "expected a volume-specific suggestion; got: " + suggestions);
    }

    @Test
    void addsARiskReviewSuggestionForHighSeverityMoves() {
        DetectedChangeResponse highSeverity = new DetectedChangeResponse(
                "STK04", InstrumentType.STOCK, LocalDate.of(2026, 8, 24),
                true, "return_z_score", BigDecimal.valueOf(4.5),
                "Large move.", Map.of("returnZScore", BigDecimal.valueOf(4.5)));

        List<String> suggestions = suggestionService.suggestActions(highSeverity, Optional.empty());

        assertTrue(suggestions.stream().anyMatch(s -> s.toLowerCase().contains("risk")
                        || s.toLowerCase().contains("position")),
                () -> "expected a risk/position-review suggestion for a high-severity move; got: " + suggestions);
    }

    @Test
    void neverProducesPrescriptiveLanguage() {
        Map<String, BigDecimal> metrics = new LinkedHashMap<>();
        metrics.put("returnZScore", BigDecimal.valueOf(4.9));
        metrics.put("volumeRatio", BigDecimal.valueOf(3.9));
        DetectedChangeResponse extreme = new DetectedChangeResponse(
                "STK04", InstrumentType.STOCK, LocalDate.of(2026, 8, 24),
                true, "return_and_volume_spike", BigDecimal.valueOf(4.9),
                "Extreme move.", metrics);
        RelatedEventResponse event = new RelatedEventResponse(
                LocalDate.of(2026, 8, 24), "stock", "earnings_beat", "desc");

        List<String> withEvent = suggestionService.suggestActions(extreme, Optional.of(event));
        List<String> withoutEvent = suggestionService.suggestActions(extreme, Optional.empty());

        for (String suggestion : withEvent) assertNoForbiddenWords(suggestion);
        for (String suggestion : withoutEvent) assertNoForbiddenWords(suggestion);
    }

    private void assertNoForbiddenWords(String suggestion) {
        String lower = suggestion.toLowerCase();
        for (String word : FORBIDDEN_WORDS) {
            assertFalse(lower.contains(word),
                    () -> "suggestion contains forbidden prescriptive language '" + word + "': " + suggestion);
        }
    }

    private DetectedChangeResponse meaningfulChange(Map<String, BigDecimal> metrics) {
        return new DetectedChangeResponse(
                "STK07", InstrumentType.STOCK, LocalDate.of(2026, 3, 30),
                true, "return_z_score", BigDecimal.valueOf(2.5),
                "Move is 2.5 standard deviations from its own recent behavior.", metrics);
    }
}
