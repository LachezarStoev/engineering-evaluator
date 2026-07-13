# Engineering Career Matrix Platform

Transparent, configurable quarterly engineering evaluations backed by GitLab, Jira, Confluence, and future connector evidence.

## Run locally

```bash
cp .env.example .env
docker compose up --build -d
```

Open `http://localhost:8088`. For production, set `DEV_AUTH_ENABLED=false`, configure `OIDC_ISSUER_URI`, use a secret manager, TLS, and a non-default database password.

## Personal connector setup

Each local installation uses the API credentials of the person running it. The Integrations screen links directly to the token pages and reports which variables are missing.

1. GitLab: open `https://git.pronetdev.com/-/user_settings/personal_access_tokens`, create a token with `read_api`, and set `GITLAB_URL` and `GITLAB_TOKEN` in `.env`.
2. Jira: open `https://id.atlassian.com/manage-profile/security/api-tokens`, create an Atlassian API token, and set `JIRA_URL`, `JIRA_EMAIL`, and `JIRA_TOKEN`.
3. Confluence: the Atlassian identity can normally be reused; set `CONFLUENCE_URL`, `CONFLUENCE_EMAIL`, and `CONFLUENCE_TOKEN`.

Never commit `.env` or paste credentials into application source. A shared production deployment should inject credentials from a secret manager. Per-user credentials in a shared deployment require OAuth or encrypted credential storage and must not be stored in the browser.

## Development

Quality checks are deterministic and suitable for CI:

```bash
# Java 21, tests, compilation, and Google Java Format verification
mvn verify

# Angular strict TypeScript, Prettier, ESLint, and production build
cd frontend && npm run quality

# Browser-level workflows
cd e2e && npm test
```

Use `mvn -pl backend spotless:apply` and `npm run format --prefix frontend` to apply the repository formatting rules locally.

```bash
mvn test
cd frontend && npm install && npm run build
cd ../e2e && npm install && npx playwright test
```

## Backend structure

- `domain`: one JPA entity or fixed-value enum per file; no catch-all domain container.
- `repository`: one public Spring Data repository interface per aggregate.
- `config`: type-safe, immutable `@ConfigurationProperties` records for application and connector settings.
- application services: synchronization, evaluation, identity discovery, workflow, exports, and advisory AI summaries.
- API layer: validated HTTP requests, RBAC checks, and explicit error responses.

The browser receives only connector status and setup URLs. It never receives a token value. Local tokens are stored in the project-root `.env`; shared deployments must inject the same environment variables from a secret manager.

## Key behavior

- Employees are discovered by normalized corporate email; stable source-system IDs should be persisted by connector implementations.
- Activity discovery is global by default: all Jira projects, GitLab repositories, and Confluence spaces visible to the configured account are searched. Access permissions remain the boundary; no project/repository allow-list is applied.
- Levels and criteria are versioned. Published rules can be evaluated; finalized quarterly evaluations are immutable.
- Missing source data is `NO_DATA`, never `FAIL`.
- A disconnected or unauthorized source is `SOURCE_UNAVAILABLE`, distinct from a healthy source returning `NO_DATA`.
- Qualitative criteria remain `NEEDS_REVIEW`.
- Every automated result contains its formula, coverage, and evidence.
- Verified identities are synchronized nightly from GitLab merge requests/review events, Jira completed tasks/story points, and Confluence documentation updates.
- Developers can dispute evidence; leads/managers can record auditable decisions; finalized reports are immutable.
- PDF and Excel exports are available per evaluation.
- AI summaries are optional and advisory. Set `AI_BASE_URL`, `AI_API_KEY`, and `AI_MODEL` for an OpenAI-compatible chat-completions endpoint; without them the system returns a deterministic summary.
- Evaluations accept either a quarter (`period: 2026-Q3`) or an inclusive custom range (`from: 2026-07-10`, `to: 2026-07-10`). Custom periods retain the configured threshold and therefore should be interpreted as a point-in-time evidence report unless the criterion itself has a daily threshold.
- Custom ranges carry an explicit IANA timezone (the UI defaults to the browser timezone) so date boundaries are reproducible. Absence and leave adjustments are intentionally not applied.
- Jira completion uses the timestamp of the transition into Jira's `statusCategory=Done`, rather than `updated` or the current status name. Velocity additionally requires `Resolution = Done`; `Duplicate`, `Cancelled`, `Won't Do`, empty, and other resolutions remain visible as excluded evidence and add neither a completed task nor Story Points.
- Story point fields are discovered from Jira field metadata across project contexts. Completed implementation tasks with missing or zero points still count as completed and are reported separately.
- QA coverage follows Jira parent/subtask and issue-link relationships. QA defects are attributed directly when linked to an implementation task, inferred when all relevant implementation tasks have one assignee, and otherwise emitted as `NEEDS_REVIEW` evidence. QA ratio uses only completed implementation tasks with a linked QA task as its denominator.

## Connector contract

New tools implement `EngineeringConnector`: connection health, user discovery by email, and evidence synchronization. Credentials belong in a secret manager and connector scopes should be read-only and least-privilege.
