package com.scamdetector.web;

public record StatsResponse(
        long totalScans,
        long lowRisk,
        long mediumRisk,
        long highRisk,
        long criticalRisk
) {
}
