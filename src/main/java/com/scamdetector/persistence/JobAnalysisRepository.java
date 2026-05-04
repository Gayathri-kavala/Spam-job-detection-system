package com.scamdetector.persistence;

import com.scamdetector.analysis.RiskLevel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JobAnalysisRepository extends JpaRepository<JobAnalysis, Long> {

    List<JobAnalysis> findTop25ByOrderByCreatedAtDesc();

    long countByRiskLevel(RiskLevel riskLevel);
}
