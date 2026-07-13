import { expect, test } from '@playwright/test';

test('employee sees transparent calculation and evidence-ready report', async ({
  page,
  request,
}) => {
  const run = Date.now();
  const email = `e2e-${run}@example.com`;
  const metricKey = `e2e_points_${run}`;
  await request.post('/api/v1/employees', {
    data: {
      email,
      displayName: 'E2E Developer',
      team: 'Platform',
      currentLevelCode: 'MID',
      targetLevelCode: 'MID',
      employmentStart: '2026-01-01',
    },
  });
  await request.post('/api/v1/criteria', {
    data: {
      code: `E2E_VELOCITY_${run}`,
      name: 'Velocity',
      sourceTool: 'jira',
      metricKey,
      evaluationType: 'AUTOMATIC',
      periodType: 'QUARTER',
      operator: '>=',
      threshold: 250,
      levelCode: 'MID',
      version: 1,
      status: 'PUBLISHED',
      effectiveFrom: '2026-01-01',
    },
  });
  await request.post('/api/v1/evidence', {
    data: {
      email,
      toolKey: 'jira',
      metricKey,
      externalId: `E2E-${run}`,
      occurredAt: '2026-07-10T10:00:00Z',
      value: 260,
      title: 'Feature',
      url: 'https://jira/E2E-1',
    },
  });
  await request.post('/api/v1/evaluations/recalculate', {
    data: {
      email,
      from: '2026-07-01',
      to: '2026-09-30',
      levelCode: 'MID',
      ruleVersion: 1,
    },
  });

  await page.goto('/');
  await page.getByPlaceholder('developer@company.com').fill(email);
  await page.getByRole('button', { name: 'Open report' }).click();
  await expect(page.getByText('E2E Developer')).toBeVisible();
  await expect(page.getByText('Pass', { exact: true })).toBeVisible();
  await expect(page.getByText(new RegExp(`SUM\\(jira\\.${metricKey}\\)`))).toBeVisible();
});

test('unknown employee receives a clear error', async ({ page }) => {
  await page.goto('/');
  await page.getByPlaceholder('developer@company.com').fill('missing@example.com');
  await page.getByRole('button', { name: 'Open report' }).click();
  await expect(page.getByText(/Employee not found/)).toBeVisible();
});

test('administrator sees published rules while advanced forms stay collapsed', async ({ page }) => {
  await page.goto('/');
  await page.getByRole('button', { name: 'Administration' }).click();
  await expect(page.getByRole('heading', { name: 'Published career matrix' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Calculate report' })).toHaveCount(0);
  await expect(page.getByText('People onboarding')).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Add employee manually' })).not.toBeVisible();
  await page.getByText('People onboarding').click();
  await expect(page.getByRole('heading', { name: 'Add employee manually' })).toBeVisible();
  await page.getByRole('button', { name: 'Integrations' }).click();
  await expect(page.getByRole('heading', { name: 'Integration health' })).toBeVisible();
});
