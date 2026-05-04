package com.scamdetector.web;

import jakarta.validation.constraints.Size;

public record AnalyzeJobRequest(
        @Size(max = 180) String title,
        @Size(max = 180) String company,
        @Size(max = 5000) String description,
        @Size(max = 220) String contactEmail,
        @Size(max = 500) String jobUrl
) {
}
