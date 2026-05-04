package com.scamdetector.analysis;

import com.scamdetector.web.AnalyzeJobRequest;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class JobAnalysisEngine {

    private static final Pattern MONEY_UPFRONT = Pattern.compile(
            "\\b(pay|payment|fee|deposit|refundable|training fee|processing fee|registration fee|equipment fee|starter kit|wire|western union|gift card|crypto|bitcoin)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern IDENTITY_DATA = Pattern.compile(
            "\\b(ssn|social security|passport|driver'?s license|bank account|routing number|credit card|tax id|aadhaar|pan card|upi|otp)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern PRESSURE = Pattern.compile(
            "\\b(urgent|immediate start|act now|limited slots|no interview|hired instantly|guaranteed job|same day offer)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern COMPENSATION = Pattern.compile(
            "\\b(\\$?\\d{3,5}\\s*(per day|daily|weekly)|earn\\s+\\$?\\d{3,5}|unlimited income|too good to be true|work 1-2 hours)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern VAGUE_ROLE = Pattern.compile(
            "\\b(data entry|virtual assistant|package reshipping|payment processor|mystery shopper|telegram assistant|whatsapp assistant)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Set<String> FREE_EMAIL_DOMAINS = Set.of(
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com", "proton.me", "protonmail.com", "icloud.com", "aol.com");

    public DetectorResult analyze(AnalyzeJobRequest request) {
        List<ScamSignal> signals = new ArrayList<>();
        String combined = normalize(request.title()) + " " + normalize(request.company()) + " " + normalize(request.description());

        addIfMatch(signals, MONEY_UPFRONT, combined, SignalCategory.MONEY_REQUEST,
                "Upfront money request", "The post appears to request fees, deposits, wire transfers, gift cards, crypto, or equipment payments.", 30);
        addIfMatch(signals, IDENTITY_DATA, combined, SignalCategory.IDENTITY_THEFT,
                "Sensitive identity or banking data", "The post asks for identity, banking, card, tax, OTP, or similar high-risk personal information.", 28);
        addIfMatch(signals, PRESSURE, combined, SignalCategory.PRESSURE_TACTIC,
                "Pressure or instant-hire language", "The post uses urgency, no-interview, guaranteed-job, or same-day-offer wording.", 16);
        addIfMatch(signals, COMPENSATION, combined, SignalCategory.COMPENSATION,
                "Unrealistic compensation claim", "The compensation language looks unusually high, vague, or disproportionate to the role.", 16);
        addIfMatch(signals, VAGUE_ROLE, combined, SignalCategory.CONTENT_QUALITY,
                "Common scam-prone role pattern", "The role matches categories frequently abused in employment scams.", 10);

        evaluateEmail(signals, request.contactEmail(), request.company());
        evaluateUrl(signals, request.jobUrl(), request.company());
        evaluateContentQuality(signals, request.description());
        evaluateVerificationGaps(signals, request);

        int score = signals.stream().mapToInt(ScamSignal::weight).sum();
        score = Math.max(0, Math.min(100, score));
        RiskLevel level = riskLevel(score);
        return new DetectorResult(score, level, summary(level, signals), signals, recommendations(level, signals));
    }

    private void evaluateEmail(List<ScamSignal> signals, String email, String company) {
        String normalizedEmail = normalize(email);
        if (normalizedEmail.isBlank()) {
            signals.add(new ScamSignal(SignalCategory.CONTACT_RISK, "Missing recruiter email",
                    "No recruiter email was provided for independent verification.", 8));
            return;
        }

        int at = normalizedEmail.lastIndexOf('@');
        if (at < 0 || at == normalizedEmail.length() - 1) {
            signals.add(new ScamSignal(SignalCategory.CONTACT_RISK, "Invalid contact email",
                    "The recruiter contact does not look like a valid email address.", 12));
            return;
        }

        String domain = normalizedEmail.substring(at + 1);
        if (FREE_EMAIL_DOMAINS.contains(domain)) {
            signals.add(new ScamSignal(SignalCategory.CONTACT_RISK, "Free email domain",
                    "The recruiter uses a personal email domain instead of a verifiable company domain.", 14));
        }

        String companyToken = normalize(company).replaceAll("[^a-z0-9]", "");
        String domainToken = domain.replaceAll("[^a-z0-9]", "");
        if (!companyToken.isBlank() && companyToken.length() >= 4 && !domainToken.contains(companyToken.substring(0, Math.min(companyToken.length(), 6)))) {
            signals.add(new ScamSignal(SignalCategory.CONTACT_RISK, "Email domain mismatch",
                    "The contact email domain does not clearly match the company name.", 8));
        }
    }

    private void evaluateUrl(List<ScamSignal> signals, String jobUrl, String company) {
        String url = normalize(jobUrl);
        if (url.isBlank()) {
            signals.add(new ScamSignal(SignalCategory.URL_RISK, "Missing job URL",
                    "No public posting URL was provided for cross-checking.", 6));
            return;
        }

        if (url.startsWith("http://")) {
            signals.add(new ScamSignal(SignalCategory.URL_RISK, "Non-HTTPS posting link",
                    "The job link uses plain HTTP instead of HTTPS.", 8));
        }

        try {
            URI uri = URI.create(url);
            String host = uri.getHost() == null ? url : uri.getHost().toLowerCase(Locale.ROOT);
            if (host.matches(".*(bit\\.ly|tinyurl|t\\.co|shorturl|rebrand\\.ly).*")) {
                signals.add(new ScamSignal(SignalCategory.URL_RISK, "Shortened or masked URL",
                        "The posting link uses a URL shortener or masked redirect.", 16));
            }
            String companyToken = normalize(company).replaceAll("[^a-z0-9]", "");
            String hostToken = host.replaceAll("[^a-z0-9]", "");
            if (!companyToken.isBlank() && companyToken.length() >= 4 && !hostToken.contains(companyToken.substring(0, Math.min(companyToken.length(), 6)))) {
                signals.add(new ScamSignal(SignalCategory.URL_RISK, "Posting domain mismatch",
                        "The posting domain does not clearly match the company name.", 8));
            }
        } catch (IllegalArgumentException ignored) {
            signals.add(new ScamSignal(SignalCategory.URL_RISK, "Malformed job URL",
                    "The job link could not be parsed as a normal URL.", 10));
        }
    }

    private void evaluateContentQuality(List<ScamSignal> signals, String description) {
        String text = normalize(description);
        if (text.length() < 180) {
            signals.add(new ScamSignal(SignalCategory.CONTENT_QUALITY, "Thin job description",
                    "The description is too short to verify responsibilities, qualifications, reporting lines, and hiring process.", 10));
        }
        long punctuationRuns = Pattern.compile("[!$]{2,}").matcher(text).results().count();
        if (punctuationRuns > 0) {
            signals.add(new ScamSignal(SignalCategory.CONTENT_QUALITY, "Promotional formatting",
                    "The post uses repeated promotional punctuation often found in low-quality listings.", 6));
        }
    }

    private void evaluateVerificationGaps(List<ScamSignal> signals, AnalyzeJobRequest request) {
        if (normalize(request.company()).isBlank()) {
            signals.add(new ScamSignal(SignalCategory.VERIFICATION, "Missing company name",
                    "A legitimate hiring process should identify the employer or staffing agency.", 12));
        }
        if (normalize(request.title()).isBlank()) {
            signals.add(new ScamSignal(SignalCategory.VERIFICATION, "Missing job title",
                    "The posting does not provide a clear role title.", 6));
        }
    }

    private void addIfMatch(List<ScamSignal> signals, Pattern pattern, String text, SignalCategory category, String title, String evidence, int weight) {
        if (pattern.matcher(text).find()) {
            signals.add(new ScamSignal(category, title, evidence, weight));
        }
    }

    private RiskLevel riskLevel(int score) {
        if (score >= 75) {
            return RiskLevel.CRITICAL;
        }
        if (score >= 50) {
            return RiskLevel.HIGH;
        }
        if (score >= 25) {
            return RiskLevel.MEDIUM;
        }
        return RiskLevel.LOW;
    }

    private String summary(RiskLevel level, List<ScamSignal> signals) {
        return switch (level) {
            case CRITICAL -> "Critical risk: multiple strong scam indicators were found. Do not send money or personal data.";
            case HIGH -> "High risk: the posting has serious warning signs and needs independent verification.";
            case MEDIUM -> "Medium risk: some warning signs were found. Verify the employer before continuing.";
            case LOW -> signals.isEmpty()
                    ? "Low risk: no major scam indicators were found from the supplied text."
                    : "Low risk: only minor verification gaps were found from the supplied text.";
        };
    }

    private List<String> recommendations(RiskLevel level, List<ScamSignal> signals) {
        List<String> items = new ArrayList<>();
        items.add("Verify the role on the company's official careers page, not through a link sent by the recruiter.");
        items.add("Contact the company through a published phone number or domain email before sharing documents.");
        if (signals.stream().anyMatch(s -> s.category() == SignalCategory.MONEY_REQUEST)) {
            items.add("Never pay application, equipment, onboarding, training, or background-check fees to receive a job.");
        }
        if (signals.stream().anyMatch(s -> s.category() == SignalCategory.IDENTITY_THEFT)) {
            items.add("Do not share SSN, passport, bank, tax, OTP, or card details until the employer is independently verified.");
        }
        if (level == RiskLevel.CRITICAL || level == RiskLevel.HIGH) {
            items.add("Save screenshots and report the posting to the job board or relevant consumer protection agency.");
        }
        return items;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
