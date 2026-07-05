# Load Testing Tool — Development Checklist

> Work top to bottom. Don't start a phase until the previous phase's milestone box is checked.

---

## Phase 0 — Validation (1–2 weeks)

### Concept validation
- [ ] Write the README first (name, one-line pitch, feature list, screenshots placeholder)
- [ ] Pick a working name and check availability (GitHub org/repo, domain, Docker Hub)
- [ ] Post concept to r/devops, r/java, or X — collect reactions
- [ ] Interview 3–5 developers: "How do you load test today? What's painful?"
- [ ] Decide go/no-go based on feedback

### Technical spike (prove the risky part)
- [ ] Install oha locally, run it against a sample API, capture JSON output (`oha --json`)
- [ ] Write a throwaway script: feed oha results + source files to an LLM API
- [ ] Verify the AI can identify a real bottleneck (test with a deliberately slow endpoint, e.g. N+1 query)
- [ ] Document what context the AI needs to give good answers (endpoint mapping, framework hints, etc.)
- [ ] Estimate token costs per analysis run

### Project setup
- [ ] Create GitHub repository (public, with license — MIT or Apache 2.0)
- [ ] Add .gitignore, README skeleton, LICENSE
- [ ] Set up issue templates and CONTRIBUTING.md skeleton
- [ ] Create project board (GitHub Projects) with these phases as columns

**Milestone: ☐ Spike proves AI analysis produces useful output, and concept got positive signal**

---

## Phase 1 — Core MVP (4–6 weeks)

### Week 1–2: Backend foundation
- [ ] Bootstrap Spring Boot project (Java 21, Spring Boot 3.x, Maven or Gradle)
- [ ] Add SQLite dependency (sqlite-jdbc + Hibernate community dialects, or plain JdbcTemplate)
- [ ] Design DB schema: `test_runs`, `test_configs`, `run_metrics`, `analysis_reports`
- [ ] Set up Flyway or Liquibase for schema migrations
- [ ] Build oha wrapper service:
  - [ ] Verify oha binary exists at startup (fail fast with clear error)
  - [ ] Build command from config (URL, method, headers, body, duration `-z`, concurrency `-c`)
  - [ ] Execute via ProcessBuilder, capture stdout/stderr
  - [ ] Parse oha JSON output into domain objects
  - [ ] Handle process failures, timeouts, and cancellation (kill process)
- [ ] REST endpoints: create test config, start run, cancel run, get run status, list history, get run detail
- [ ] Unit tests for oha command building and output parsing
- [ ] Integration test: run oha against a stub server (WireMock) end to end

### Week 3–4: Web UI
- [ ] Choose stack: Thymeleaf + htmx (recommended) — no separate frontend build
- [ ] Page: new test form (URL, method, headers, body, duration, concurrency, request count)
- [ ] Input validation (valid URL, sane limits on concurrency/duration)
- [ ] Page: live test progress — stream status via SSE (Spring `SseEmitter`)
- [ ] Page: results detail — latency percentiles (p50/p90/p95/p99), RPS, error rate, status code breakdown
- [ ] Charts: latency distribution + RPS over time (Chart.js is enough)
- [ ] Page: test history list with timestamps and summary stats
- [ ] Basic navigation and consistent layout
- [ ] Safety guard: confirmation warning when target is not localhost / private IP ("only test systems you own")

### Week 5–6: Docker packaging & polish
- [ ] Multi-stage Dockerfile: build stage + slim runtime (JRE + oha binary)
- [ ] Install oha in image (download release binary for correct arch, verify checksum)
- [ ] Support amd64 and arm64 (multi-arch build with buildx)
- [ ] Volume mount for SQLite file so history survives container restarts
- [ ] Configuration via environment variables (port, DB path, defaults)
- [ ] docker-compose.yml example with a sample target app to test against
- [ ] Health check endpoint + Docker HEALTHCHECK
- [ ] Graceful shutdown (kill running oha process, mark run as aborted)
- [ ] Test on a clean machine: `docker run` → working UI in under 2 minutes
- [ ] Ask 2–3 people to try it cold and watch where they get stuck

**Milestone: ☐ A stranger can `docker run` the image and load-test a URL in under 2 minutes**

---

## Phase 2 — AI Analysis (3–4 weeks)

### Week 1: Provider abstraction
- [ ] Define `AiProvider` interface (analyze(context) → report)
- [ ] Implement Anthropic API provider
- [ ] Implement OpenAI-compatible provider (also covers Ollama/local models via base URL)
- [ ] Config: provider, API key, model, base URL — all via env vars, never stored in DB
- [ ] Graceful degradation: tool fully works without AI configured (analysis tab shows setup instructions)

### Week 2: Codebase ingestion
- [ ] Support codebase via Docker volume mount (`-v ./myproject:/workspace/target-code:ro`)
- [ ] File scanner: respect .gitignore, skip binaries/node_modules/build dirs
- [ ] Language detection and endpoint-to-code mapping heuristics (start with Spring/Express/FastAPI annotations and routes)
- [ ] Context builder: select relevant files for the slow endpoints, stay within token budget
- [ ] Handle large repos: prioritize controller/service/repository layers, summarize the rest

### Week 3: Analysis pipeline & report
- [ ] Prompt engineering: oha metrics + relevant code → structured findings (iterate here, this is the product)
- [ ] Report structure: finding, severity, file:line reference, explanation, suggested fix
- [ ] Store reports in SQLite linked to test runs
- [ ] Report UI page: readable findings with code snippets, copy-fix buttons
- [ ] "Analyze" button on completed runs (async job, progress indicator)
- [ ] Error handling: API failures, rate limits, oversized codebases — clear user messages

### Week 4: Quality pass
- [ ] Test against 3+ real sample apps with planted bottlenecks (N+1 query, missing index, sync blocking call, missing cache)
- [ ] Measure: does the AI find the planted issue? Tune prompts until ≥ most cases pass
- [ ] Add token/cost estimate shown before analysis runs
- [ ] Document AI setup in README (per provider)

**Milestone: ☐ AI correctly identifies planted bottlenecks in sample apps and produces a report worth screenshotting**

---

## Phase 3 — Launch (2 weeks)

### Week 1: Launch assets
- [ ] Polish README: hero GIF of live test + AI report, quickstart, badges
- [ ] Record 2–3 minute demo video
- [ ] Create sample target app repo (deliberately slow API for demos)
- [ ] Write docs: configuration reference, AI provider setup, FAQ, troubleshooting
- [ ] Publish image to Docker Hub / GHCR with versioned tags
- [ ] Tag v0.1.0 release with release notes
- [ ] Final pass: issue templates, CONTRIBUTING.md, code of conduct, "good first issue" labels on 5+ issues

### Week 2: Launch execution
- [ ] Show HN post ("Show HN: Load tester that reads your code and tells you why it's slow") — post morning US time, weekday
- [ ] Product Hunt launch
- [ ] Reddit posts (r/devops, r/java, r/selfhosted, r/opensource — follow each sub's rules)
- [ ] Dev.to / Hashnode article: "Why I built X" with technical deep-dive
- [ ] Respond to every comment and issue within hours during launch week
- [ ] Track: GitHub stars, Docker pulls, issues opened, feature requests

**Milestone: ☐ Launched publicly, first external users filed issues or feedback**

---

## Phase 4 — Retention & Growth (ongoing)

### Regression detection
- [ ] Side-by-side run comparison UI (diff of percentiles, RPS, errors)
- [ ] Baseline runs: mark a run as baseline, flag regressions against it
- [ ] Scheduled recurring tests (cron-style config)
- [ ] Notifications on regression (webhook first, then Slack/email)

### CI/CD integration
- [ ] CLI mode / API endpoint suitable for pipelines
- [ ] Threshold config (fail if p95 > X ms or error rate > Y%)
- [ ] Official GitHub Action
- [ ] Exit codes + machine-readable output (JSON)

### Usability
- [ ] OpenAPI/Swagger import → auto-generate test configs for all endpoints
- [ ] Test suites: run multiple endpoint tests in sequence
- [ ] Export results (JSON/CSV), shareable report links

### Distributed mode (only if demand shows up in issues)
- [ ] Design worker agent: outbound connection to coordinator (Locust-style)
- [ ] Worker Docker image
- [ ] Aggregate metrics across workers
- [ ] Optional ngrok convenience docs for NAT'd coordinators

### Community
- [ ] Triage issues weekly, label and respond
- [ ] Monthly release cadence with changelog
- [ ] Prioritize roadmap by issue reactions (👍 counts), not personal preference

**Milestone: ☐ Retained users returning weekly, first external contributor merged**

---

## Cross-cutting (check throughout every phase)

- [ ] Never log or store API keys; secrets only via env vars
- [ ] README stays accurate with every merged change
- [ ] Every feature has at least a happy-path test
- [ ] Semantic versioning from v0.1.0
- [ ] Keep a CHANGELOG.md
- [ ] Ethical-use notice in README and UI (test only systems you own or have permission to test)
