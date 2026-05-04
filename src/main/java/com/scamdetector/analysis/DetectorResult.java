package com.scamdetector.analysis;

import java.util.List;

public record DetectorResult(
        int riskScore,
        RiskLevel riskLevel,
        String summary,
        List<ScamSignal> signals,
        List<String> recommendations
) {
}
