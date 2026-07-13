# Engineering Career Matrix

[![Continuous Integration](https://github.com/LachezarStoev/engineering-evaluator/actions/workflows/ci.yml/badge.svg)](https://github.com/LachezarStoev/engineering-evaluator/actions/workflows/ci.yml)

Transparent engineering evaluations backed by Jira, GitLab, and Confluence evidence.

The platform collects read-only activity, calculates versioned criteria with deterministic rules,
shows the evidence behind every result, and keeps AI and management judgment in separate review
layers. It supports career conversations; it is not an automatic promotion engine or an employee
ranking system.

## At a glance

| Evidence                   | Calculation                   | Fairness                          | Delivery              |
| -------------------------- | ----------------------------- | --------------------------------- | --------------------- |
| Jira, GitLab, Confluence   | Versioned deterministic rules | Visible formulas and source links | Angular + Spring Boot |
| Global identity discovery  | Explicit coverage states      | Developer dispute workflow        | PostgreSQL + Docker   |
| Read-only connectors       | AI is advisory only           | Human final decision              | PDF and Excel exports |
| Generic engineering tracks | Common + track criteria       | No hidden productivity score      | Full E2E verification |

## How it works

| **① Collect**              | **② Calculate**                   | **③ Review**                           |
| :------------------------- | :-------------------------------- | :------------------------------------- |
| Jira · GitLab · Confluence | Versioned deterministic formulas  | Advisory AI summary                    |
| Read-only synchronization  | Metrics and coverage states       | Developer verification or dispute      |
| Normalize and deduplicate  | Reproducible source-linked result | Manager decision, finalization, export |

**Collect → Calculate → Review**

The boundary is intentional: connectors establish what happened, formulas calculate measurable
results, AI explains supplied facts, and authorized people make the career decision.

## Core principles

- Every automatic result links to its source evidence.
- Missing or unavailable data is never silently treated as failure.
- Developers and managers see the same formulas, coverage, and evidence.
- Qualitative or ambiguous criteria remain `NEEDS_REVIEW`.
- AI cannot alter evidence, formulas, scores, levels, or final decisions.
- Rules and finalized evaluations are versioned and reproducible.
- Story points, comments, commits, and reviews are indicators—not a hidden productivity score.

## User flow

1. Enter a corporate email. An unknown employee is provisioned only when a connector returns one
   unambiguous exact identity match.
2. Select an inclusive `From` / `To` period and the evaluated level.
3. Collect evidence and create the report.
4. Inspect formulas, coverage, and individual Jira/GitLab/Confluence records.
5. The developer verifies or disputes the evidence.
6. A lead or manager records a reasoned decision and finalizes the report.
7. Export the reviewed result as PDF or Excel.

Any custom period is supported and boundaries use the selected IANA timezone. Every criterion owns
an explicit proration policy: `ALLOWED`, `PROGRESS_ONLY`, or `FORBIDDEN`. Partial periods show the
observed value, proportional expected pace, and full cadence target. `ASSESSMENT` produces formal
PASS/FAIL only when the published policy permits it; progress-only rules preserve the valid evidence
without manufacturing a promotion decision from an incomplete period.

The report displays one active evaluation at a time. Its Data Readiness panel separates connector
health, identity verification, and period evidence (`SYNCED`, `NO_ACTIVITY`, or unavailable), so a
missing identity is never presented as a connector failure.

## Identity discovery

Corporate email is the human identifier; stable source IDs are stored after discovery:

- Jira searches globally and stores the Atlassian `accountId`.
- Confluence reuses the verified Atlassian identity.
- GitLab searches by email and then by its local part because profile emails may be private.

An exact email match is preferred. Until an Atlassian organization directory token is available,
Jira may hide email addresses; in that case a single strong corporate-email/display-name match
(`angel.angelov` → `Angel A.`) is accepted with an auditable match type and confidence. Multiple
or weaker candidates remain unverified and must be confirmed from the report or Integrations
screen. Search scope is global only within the projects, repositories, and spaces visible to the
configured read-only accounts.

## Deterministic metrics

Each published criterion contains a source, normalized metric key, aggregation, operator or range,
threshold, cadence, proration policy, scope, track/team applicability, mandatory/supporting flag,
evaluation type, human rubric, level, version, and effective date.

```text
Velocity       = SUM(jira.story_points)
Reviews        = COUNT(gitlab.review_comments)
Documentation  = COUNT(confluence.documentation_updates)
QA Ratio       = COUNT(jira.qa_defects)
                 / COUNT(jira.qa_tested_completed_tasks) * 100
```

### Jira velocity

An implementation issue contributes when the verified developer was assigned, its history enters
Jira's `Done` category during the selected period, `Resolution = Done`, and it is not a QA issue.

- Missing or zero Story Points contribute `0` but remain visible as a data-quality gap.
- Duplicate, cancelled, empty, and other resolutions are excluded with an explicit reason.
- Completion transition time—not current status or last update time—determines the period.
- There is no project allow-list; all visible projects, including ODIN and JORD, are searched.

Comments, worklogs, issue creation, status/field changes, and work on other people's issues appear
in the activity timeline. They provide context but do not automatically become velocity.

### QA ratio

The denominator contains completed implementation tasks with a linked QA task. A defect is charged
only when it directly links to one implementation task or all relevant implementation work has one
verified assignee. Otherwise it becomes `qa_defects_needs_review`.

Jira Priority is shown as a breakdown, not converted into an arbitrary weighted score. Production
incidents are not attributed when source relationships cannot prove causality.

### GitLab and Confluence

GitLab records authored merge requests, commits, review comments, project context, and source URLs.
Review count is candidate evidence; a person confirms whether feedback was meaningful.

Confluence records page contributions by the verified Atlassian account. Counts represent activity,
not documentation quality or organizational impact.

## Result and coverage states

| State                | Meaning                                                 |
| -------------------- | ------------------------------------------------------- |
| `PASS`               | Available evidence satisfies the published formula      |
| `FAIL`               | Available evidence does not satisfy the formula         |
| `NO_DATA`            | The source is healthy but no qualifying evidence exists |
| `SOURCE_UNAVAILABLE` | The connector is missing, unauthorized, or unhealthy    |
| `NEEDS_REVIEW`       | Attribution or the criterion requires human judgment    |

Every result exposes formula, measured value, threshold, coverage, and source records.

## Framework v2 and engineering tracks

Framework v2 implements the final seven-level progression: **Junior I → Junior II → Mid I → Mid II
→ Senior I → Senior II → Principal**. The earlier five-level framework remains immutable as version
1 so historical reports stay reproducible.

| Configuration layer | Purpose                                                                                                                |
| ------------------- | ---------------------------------------------------------------------------------------------------------------------- |
| Common criteria     | Company expectations shared by every engineer                                                                          |
| Engineering track   | Backend, Frontend, General, or a future configurable discipline                                                        |
| Team criteria       | A deliberately published team-specific addition                                                                        |
| Competency baseline | Technical skills, independence, reviews, ownership, mentoring, architecture, business understanding, cross-team impact |

Backend and Frontend are preloaded tracks; Full-stack is a draft example. Administrators can add
Data, QA Automation, Platform, Mobile, or another track without changing application code. New
external systems still require an `EngineeringConnector`, but new levels, tracks, rubrics, ranges,
cadences, and criteria are configuration.

The quantitative targets from the approved framework are seeded as version 2. Ownership,
mentoring, standards, architecture, review quality, and strategic impact use visible human rubrics
and remain `NEEDS_REVIEW`; the system never converts them into invented repository scores.

## AI and human review

AI runs after calculation and may summarize evidence, coverage gaps, themes, or questions for a
reviewer. It uses only supplied facts and falls back to a deterministic summary when unavailable.

AI cannot infer missing Story Points, attribute ambiguous defects, calculate `PASS`/`FAIL`, select a
career level, or finalize an evaluation. Developers can dispute evidence; managers must record a
reason for decisions. Finalized reports are immutable and auditable.

## Access control

| Role                  | Intended access                             |
| --------------------- | ------------------------------------------- |
| `DEVELOPER`           | Own reports, evidence, and disputes         |
| `TEAM_LEAD`           | Direct reports                              |
| `ENGINEERING_MANAGER` | Team reports, decisions, and finalization   |
| `EVALUATOR_ADMIN`     | Career matrix versioning                    |
| `INTEGRATION_ADMIN`   | Connectors, identities, and synchronization |
| `ORGANIZATION_ADMIN`  | Employee and organization configuration     |
| `HR` / `AUDITOR`      | Policy-authorized reporting access          |

Local development can use `X-User-Email` and `X-Roles`. Production disables dev authentication and
reads the `email` and `roles` claims from the configured OIDC JWT. Angular visibility is only UX;
Spring Security enforces every protected endpoint.

## Configuration

```bash
cp .env.example .env
```

Configure least-privilege credentials:

- GitLab: `GITLAB_URL`, `GITLAB_TOKEN` with `read_api`.
- Jira: `JIRA_URL`, `JIRA_EMAIL`, `JIRA_TOKEN`.
- Confluence: `CONFLUENCE_URL`, `CONFLUENCE_EMAIL`, `CONFLUENCE_TOKEN`.

Secrets are read only by the backend. `.env` is ignored by Git; production must inject secrets from
a secret manager and use `DEV_AUTH_ENABLED=false`, OIDC, TLS, backups, and a data-retention policy.

Local Docker Compose runs load connector credentials directly from the project `.env`. This avoids
unrelated shell variables such as `JIRA_TOKEN` overriding the credentials selected for this app.
Production platforms should inject the same variables directly into the backend container.

## Run

Requirements: Java 21, Maven, Node.js 22, npm 10, and Docker for E2E testing.

### Docker with persistent PostgreSQL

```bash
docker compose up --build -d
```

Open <http://localhost:8088>. Compose starts Angular, Spring Boot, and PostgreSQL 17 with a persistent
`evaluator-data` volume.

### Local development

```bash
# Terminal 1
cd backend
mvn spring-boot:run

# Terminal 2
cd frontend
npm ci
npm start
```

Without `DATABASE_URL`, the backend uses temporary in-memory H2. Restarting it deletes local data;
use Docker/PostgreSQL for persistent work.

## Tests and GitHub Actions

```bash
mvn verify                    # Java tests, Flyway/JPA, formatting
npm run quality --prefix frontend
npm test --prefix e2e        # isolated PostgreSQL + backend + frontend + Playwright
```

The E2E suite owns port `18088` and a separate Docker Compose project. It always removes its
containers, network, and database volume. On failure it preserves Playwright traces and Compose
logs under `e2e/test-results`.

GitHub Actions runs backend and frontend checks in parallel, then runs the full deployment-level
E2E suite. Failed E2E diagnostics are uploaded as a short-lived artifact.

## Extending the platform

New source integrations implement `EngineeringConnector` and return normalized evidence. Career
rules remain outside connectors.

When adding a track or metric:

1. Create or reuse a generic engineering track.
2. Agree on definitions, exclusions, attribution, cadence, and ambiguous examples.
3. Define a stable metric key, evidence schema, coverage rule, and human rubric where required.
4. Implement paginated, deduplicated, rate-limit-aware extraction only when the source is new.
5. Configure deterministic formulas independently of AI.
6. Test included, excluded, missing, ambiguous, track, RBAC, and historical-version cases.
7. Preview the rule, show its exact calculation to developers, and publish it with an effective date.
8. Pilot and calibrate before using it in promotion decisions.

Prefer a smaller set of trusted metrics over a large set of weak proxies.
