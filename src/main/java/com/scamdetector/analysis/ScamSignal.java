package com.scamdetector.analysis;

public record ScamSignal(
        SignalCategory category,
        String title,
        String evidence,
        int weight
) {
}
