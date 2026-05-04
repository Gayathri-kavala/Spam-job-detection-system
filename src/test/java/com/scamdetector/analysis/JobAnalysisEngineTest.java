package com.scamdetector.analysis;

import com.scamdetector.web.AnalyzeJobRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JobAnalysisEngineTest {

    private final JobAnalysisEngine engine = new JobAnalysisEngine();

    @Test
    void flagsCriticalScamSignals() {
        AnalyzeJobRequest request = new AnalyzeJobRequest(
                "Remote Data Entry Assistant",
                "Global Hiring Team",
                "No interview. Earn $1200 weekly. Pay a refundable equipment fee with gift card and send SSN, bank account and OTP.",
                "recruiter@gmail.com",
                "http://bit.ly/job-offer"
        );

        DetectorResult result = engine.analyze(request);

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.CRITICAL);
        assertThat(result.riskScore()).isGreaterThanOrEqualTo(75);
        assertThat(result.signals()).extracting(ScamSignal::category)
                .contains(SignalCategory.MONEY_REQUEST, SignalCategory.IDENTITY_THEFT, SignalCategory.CONTACT_RISK);
    }

    @Test
    void keepsDetailedLegitimatePostingLowRisk() {
        AnalyzeJobRequest request = new AnalyzeJobRequest(
                "Senior Java Backend Engineer",
                "Northstar Systems",
                "Northstar Systems is hiring a Senior Java Backend Engineer to build distributed APIs with Spring Boot, PostgreSQL, Kafka, and observability tooling. The process includes recruiter screen, technical interview, system design interview, and references. Benefits, responsibilities, and location policies are documented on the official careers page.",
                "careers@northstarsystems.com",
                "https://northstarsystems.com/careers/senior-java-backend-engineer"
        );

        DetectorResult result = engine.analyze(request);

        assertThat(result.riskLevel()).isEqualTo(RiskLevel.LOW);
        assertThat(result.riskScore()).isLessThan(25);
    }
}
