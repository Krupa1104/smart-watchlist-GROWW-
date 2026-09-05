package com.groww.smart_watchlist.service;

import com.groww.smart_watchlist.dto.DetectedChangeResponse;
import com.groww.smart_watchlist.dto.RelatedEventResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

// Deliberately simple and rule-based — NOT a recommendation engine, NOT ML.
// Every suggestion is a next investigative step ("go look at this"), never
// a verdict on what to do with money. No suggestion here ever says buy,
// sell, hold, invest, or predicts future price direction — see the
// hackathon brief's explicit non-prescriptive requirement. Suggestions are
// only produced for meaningful changes; a normal move has nothing to act on.
@Service
public class SuggestionService {

    private static final BigDecimal HIGH_VOLUME_RATIO = BigDecimal.valueOf(2.0);
    private static final BigDecimal HIGH_SEVERITY = BigDecimal.valueOf(3.0);

    public List<String> suggestActions(DetectedChangeResponse detected, Optional<RelatedEventResponse> relatedEvent) {
        List<String> suggestions = new ArrayList<>();
        if (detected == null || !detected.meaningful()) {
            return suggestions; // a normal move has nothing to suggest
        }

        if (relatedEvent != null && relatedEvent.isPresent()) {
            suggestions.add("Review the related event below — it may explain this move.");
        } else {
            suggestions.add("No recorded event found — treat this as a statistical anomaly and verify independently.");
        }

        suggestions.add("Monitor this instrument closely over the next few sessions before drawing conclusions.");

        BigDecimal volumeRatio = detected.metrics() == null ? null : detected.metrics().get("volumeRatio");
        if (volumeRatio != null && volumeRatio.compareTo(HIGH_VOLUME_RATIO) >= 0) {
            suggestions.add("Volume is well above average — check whether this is a short-lived spike before acting.");
        }

        if (detected.severityScore() != null && detected.severityScore().compareTo(HIGH_SEVERITY) >= 0) {
            suggestions.add("This is a large move by this instrument's own recent standards — consider reviewing your position or risk exposure.");
        }

        return suggestions;
    }
}
