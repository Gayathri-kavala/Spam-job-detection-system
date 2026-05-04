package com.scamdetector.web;

import com.scamdetector.analysis.DetectorResult;
import com.scamdetector.analysis.JobAnalysisEngine;
import com.scamdetector.analysis.RiskLevel;
import com.scamdetector.persistence.JobAnalysis;
import com.scamdetector.persistence.JobAnalysisRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/analyses")
public class JobAnalysisController {

    private final JobAnalysisEngine engine;
    private final JobAnalysisRepository repository;

    public JobAnalysisController(JobAnalysisEngine engine, JobAnalysisRepository repository) {
        this.engine = engine;
        this.repository = repository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobAnalysis analyze(@Valid @RequestBody AnalyzeJobRequest request) {
        DetectorResult result = engine.analyze(request);
        JobAnalysis analysis = new JobAnalysis();
        analysis.setTitle(blankToFallback(request.title(), "Untitled role"));
        analysis.setCompany(blankToFallback(request.company(), "Unknown company"));
        analysis.setDescription(blankToFallback(request.description(), ""));
        analysis.setContactEmail(blankToFallback(request.contactEmail(), ""));
        analysis.setJobUrl(blankToFallback(request.jobUrl(), ""));
        analysis.setRiskScore(result.riskScore());
        analysis.setRiskLevel(result.riskLevel());
        analysis.setSummary(result.summary());
        analysis.setSignals(result.signals().stream()
                .map(signal -> signal.category() + " | " + signal.title() + " | " + signal.evidence() + " | +" + signal.weight())
                .toList());
        analysis.setRecommendations(result.recommendations());
        analysis.setCreatedAt(Instant.now());
        return repository.save(analysis);
    }

    @GetMapping
    public List<JobAnalysis> list() {
        return repository.findTop25ByOrderByCreatedAtDesc();
    }

    @GetMapping("/stats")
    public StatsResponse stats() {
        return new StatsResponse(
                repository.count(),
                repository.countByRiskLevel(RiskLevel.LOW),
                repository.countByRiskLevel(RiskLevel.MEDIUM),
                repository.countByRiskLevel(RiskLevel.HIGH),
                repository.countByRiskLevel(RiskLevel.CRITICAL)
        );
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        repository.deleteById(id);
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
