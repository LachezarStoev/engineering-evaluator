import { expect, test } from "@playwright/test";

test("backend developer receives a seven-level, evidence-backed framework v2 assessment", async ({
  page,
  request,
}) => {
  const run = Date.now();
  const email = `backend-e2e-${run}@example.com`;
  const metricKey = `backend_quality_${run}`;
  const criterionName = `Backend quality ${run}`;

  await request.post("/api/v1/employees", {
    data: {
      email,
      displayName: "Backend E2E Developer",
      team: "Platform",
      trackCode: "BACKEND",
      currentLevelCode: "MID_I",
      targetLevelCode: "MID_I",
      employmentStart: "2026-01-01",
    },
  });
  await request.post("/api/v1/criteria", {
    data: {
      code: `BACKEND_E2E_${run}`,
      name: criterionName,
      sourceTool: "jira",
      metricKey,
      evaluationType: "AUTOMATIC",
      periodType: "QUARTER",
      prorationPolicy: "ALLOWED",
      scope: "TRACK",
      trackCode: "BACKEND",
      mandatory: true,
      operator: ">=",
      threshold: 1,
      aggregation: "COUNT",
      levelCode: "MID_I",
      version: 2,
      status: "PUBLISHED",
      effectiveFrom: "2026-07-01",
    },
  });
  await request.post("/api/v1/evidence", {
    data: {
      email,
      toolKey: "jira",
      metricKey,
      externalId: `BACKEND-${run}`,
      occurredAt: "2026-07-10T10:00:00Z",
      value: 1,
      title: "Backend quality improvement",
      url: "https://jira/BACKEND-1",
    },
  });
  await request.post("/api/v1/evidence", {
    data: {
      email,
      toolKey: "jira",
      metricKey,
      externalId: `BACKEND-EARLY-${run}`,
      occurredAt: "2026-07-05T10:00:00Z",
      value: 1,
      title: "Earlier backend quality improvement",
      url: "https://jira/BACKEND-0",
    },
  });
  await request.post(
    `/api/v1/employees/${encodeURIComponent(email)}/prepare-report`,
    {
      data: {
        from: "2026-07-01",
        to: "2026-07-06",
        timezone: "UTC",
        mode: "ASSESSMENT",
        levelCode: "MID_I",
        ruleVersion: 2,
      },
    },
  );

  await page.goto("/");
  await page.getByPlaceholder("developer@company.com").fill(email);
  await page.getByRole("button", { name: "Open report" }).click();
  await expect(page.getByText("Backend E2E Developer")).toBeVisible();
  await expect(page.getByText("Backend Engineering")).toBeVisible();

  await page.getByRole("button", { name: "Run level assessment" }).click();
  await expect(
    page.getByRole("heading", { name: "Evidence-based level assessment" }),
  ).toBeVisible({
    timeout: 120_000,
  });
  await expect(
    page.locator(".criterion-title").filter({ hasText: criterionName }),
  ).toBeVisible();
  await expect(page.getByText("Independent delivery")).toBeVisible();
  await expect(page.locator(".level-fit-card")).toHaveCount(7);
  await expect(page.locator(".executive-metric")).toHaveCount(4);
  await expect(
    page.getByRole("heading", { name: "Work visible in this period" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Evidence over time" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Daily engineering footprint" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Change from previous report" }),
  ).toBeVisible();
  await expect(page.locator(".activity-chart")).not.toContainText("15 Jul");
  await expect(page.locator(".activity-chart")).toContainText("13 Jul");
  await expect(page.locator(".activity-chart")).not.toContainText("14 Jul");
  await expect(page.locator(".activity-heatmap span")).not.toHaveCount(0);
  await expect(
    page.locator(".result-row").filter({ hasText: criterionName }),
  ).toContainText("Pass");
  await page.getByRole("button", { name: "Show activity details" }).click();
  await expect(page.getByLabel("Source")).toBeVisible();
  await expect(page.locator(".activity-toolbar select")).toHaveCount(2);
});

test("unknown employee receives a clear discovery error", async ({ page }) => {
  await page.goto("/");
  await page
    .getByPlaceholder("developer@company.com")
    .fill(`missing-${Date.now()}@example.com`);
  await page.getByRole("button", { name: "Open report" }).click();
  await expect(
    page.getByText(/Employee not found in the configured integrations/),
  ).toBeVisible();
});

test("frontend developer receives frontend criteria through the same generic engine", async ({
  page,
  request,
}) => {
  const run = Date.now();
  const email = `frontend-e2e-${run}@example.com`;
  const metricKey = `accessibility_${run}`;
  const criterionName = `Frontend accessibility ${run}`;
  await request.post("/api/v1/employees", {
    data: {
      email,
      displayName: "Frontend E2E Developer",
      team: "Web",
      trackCode: "FRONTEND",
      currentLevelCode: "MID_I",
      targetLevelCode: "MID_I",
      employmentStart: "2026-01-01",
    },
  });
  await request.post("/api/v1/criteria", {
    data: {
      code: `FRONTEND_E2E_${run}`,
      name: criterionName,
      sourceTool: "lighthouse",
      metricKey,
      evaluationType: "AUTOMATIC_WITH_REVIEW",
      periodType: "QUARTER",
      prorationPolicy: "ALLOWED",
      scope: "TRACK",
      trackCode: "FRONTEND",
      mandatory: false,
      operator: "<=",
      threshold: 0,
      aggregation: "SUM",
      levelCode: "MID_I",
      version: 2,
      status: "PUBLISHED",
      effectiveFrom: "2026-07-13",
    },
  });
  await request.post("/api/v1/evidence", {
    data: {
      email,
      toolKey: "lighthouse",
      metricKey,
      externalId: `LIGHTHOUSE-${run}`,
      occurredAt: "2026-07-10T10:00:00Z",
      value: 0,
      title: "Accessibility CI run",
    },
  });

  await page.goto("/");
  await page.getByPlaceholder("developer@company.com").fill(email);
  await page.getByRole("button", { name: "Open report" }).click();
  await expect(page.getByText("Frontend Engineering")).toBeVisible();
  await page.getByRole("button", { name: "Run level assessment" }).click();
  await expect(page.getByText(criterionName)).toBeVisible({ timeout: 120_000 });
  await expect(page.getByText("FRONTEND SIGNALS")).toBeVisible();
  await expect(page.getByText("Merged UI changes")).toBeVisible();
  await expect(
    page.locator(".result-row").filter({ hasText: criterionName }),
  ).toContainText("Pass");
});

test("administrator sees the seven-level matrix and can add a new generic track", async ({
  page,
  request,
}) => {
  const assignmentEmail = `track-assignment-${Date.now()}@example.com`;
  await request.post("/api/v1/employees", {
    data: {
      email: assignmentEmail,
      displayName: "Track Assignment Developer",
      team: "Web",
      trackCode: "GENERAL",
      currentLevelCode: "MID_I",
      targetLevelCode: "MID_I",
      employmentStart: "2026-01-01",
    },
  });
  await page.goto("/");
  await page.getByRole("button", { name: "Administration" }).click();
  await expect(
    page.getByRole("heading", { name: "Published career matrix" }),
  ).toBeVisible();
  await expect(page.getByText("7 LEVELS")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Backend Engineering" }),
  ).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Frontend Engineering" }),
  ).toBeVisible();
  await expect(page.getByText("People onboarding")).toBeVisible();
  await expect(
    page.getByRole("heading", { name: "Add employee manually" }),
  ).not.toBeVisible();
  await page.getByText("People onboarding").click();
  const assignment = page.locator(".assignment-box");
  await assignment.getByLabel("Employee email").fill(assignmentEmail);
  await assignment.getByLabel("Track").selectOption("FRONTEND");
  await assignment.getByRole("button", { name: "Assign track" }).click();
  await expect(page.getByText("Employee track updated")).toBeVisible();

  await page.getByText("Matrix versioning").click();
  const trackPanel = page
    .getByRole("heading", { name: "Create an engineering track" })
    .locator("..");
  const suffix = Date.now().toString().slice(-7);
  await trackPanel.getByLabel("Code").fill(`QA_${suffix}`);
  await trackPanel.getByLabel("Name").fill(`QA Automation ${suffix}`);
  await trackPanel
    .getByLabel("Description")
    .fill("Automated quality engineering track");
  await trackPanel.getByRole("button", { name: "Create track draft" }).click();
  await expect(page.getByText("Track draft created")).toBeVisible();

  await page.getByRole("button", { name: "Integrations" }).click();
  await expect(
    page.getByRole("heading", { name: "Integration health" }),
  ).toBeVisible();
});
