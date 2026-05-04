# Fake Job / Scam Detector System

A ready-to-run full-stack system for detecting suspicious job postings and recruitment scam patterns.

## Features

- Java Spring Boot REST backend
- Static responsive web UI served by the backend
- Explainable risk scoring engine with weighted scam signals
- Persistent scan history with H2 database
- Dashboard metrics and recent scan table
- API endpoints for integrations
- Unit tests for the detector engine

## Run

```powershell
mvn spring-boot:run
```

Open:

```text
http://localhost:8080
```

H2 console:

```text
http://localhost:8080/h2-console
```

JDBC URL:

```text
jdbc:h2:file:./data/scamdetector
```

## API

Analyze a job:

```http
POST /api/analyses
Content-Type: application/json
```

```json
{
  "title": "Remote Data Entry Assistant",
  "company": "Global Hiring Team",
  "description": "We pay weekly. No interview. Send your bank details and pay a refundable equipment fee.",
  "contactEmail": "recruiter@gmail.com",
  "jobUrl": "http://example-job-offer.com"
}
```

List history:

```http
GET /api/analyses
```

Dashboard stats:

```http
GET /api/analyses/stats
```

Delete one scan:

```http
DELETE /api/analyses/{id}
```

## Notes

The detector is an explainable rules-based AI safety layer. It does not prove that a job is legitimate or fraudulent; it highlights risk patterns and gives practical next steps.
