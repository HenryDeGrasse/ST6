/**
 * Playwright acceptance coverage mapped to PRD §18.
 *
 * These scenarios run the PA host stub and mock the API boundary so the
 * micro-frontend can be exercised deterministically in CI without depending
 * on an external backend deployment.
 *
 * Test categories:
 * - [SMOKE] = runs on every PR (Gate 7)
 * - [FULL]  = deeper acceptance coverage, suitable for nightly runs
 */
import { expect, test } from '@playwright/test';
import {
  buildPlan,
  buildCommit,
  mondayIso,
  installMockApi,
  MOCK_COMMIT_ID as COMMIT_ID,
  MOCK_REPORT_USER_ID as REPORT_USER_ID,
} from './helpers';

// ── File-local constants ───────────────────────────────────────────────────
// MOCK_PLAN_ID, MOCK_COMMIT_ID etc. are used via the imported builders and
// installMockApi; only the IDs that appear directly in test assertions are
// kept as local aliases above.

const now = '2026-03-12T12:00:00Z';

test.describe('IC Create → Lock', () => {
  test('[SMOKE] IC can create a weekly plan and lock it', async ({ page }) => {
    await installMockApi(page, { createPlanSeedsCommits: true });

    await page.goto('/');

    await expect(page.getByTestId('pa-host-shell')).toBeVisible();
    await expect(page.getByTestId('create-plan-btn')).toBeVisible();

    await page.getByTestId('create-plan-btn').click();
    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('commit-row-' + COMMIT_ID)).toBeVisible();

    await page.getByTestId('lock-btn').click();
    // Lock btn opens a ConfirmDialog — click confirm to proceed
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();
    await expect(page.getByTestId('plan-state')).toContainText('Locked');
  });
});

test.describe('IC Reconcile → Carry-Forward', () => {
  test('[SMOKE] IC can reconcile and carry forward', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({
        state: 'LOCKED',
        lockType: 'ON_TIME',
        lockedAt: now,
      }),
      commits: [buildCommit()],
    });

    await page.goto('/');

    await expect(page.getByTestId('start-reconciliation-btn')).toBeVisible();
    await page.getByTestId('start-reconciliation-btn').click();
    await expect(page.getByTestId('reconciliation-view')).toBeVisible();

    await page.getByTestId(`reconcile-status-${COMMIT_ID}`).selectOption('DONE');
    await page.getByTestId(`reconcile-actual-${COMMIT_ID}`).fill('Delivered the planning workflow end to end.');
    await page.getByTestId(`reconcile-save-${COMMIT_ID}`).click();
    await page.getByTestId('reconcile-submit').click();
    // reconcile-submit opens a ConfirmDialog — click confirm to proceed
    await expect(page.getByTestId('confirm-dialog')).toBeVisible();
    await page.getByTestId('confirm-dialog-confirm').click();

    await expect(page.getByTestId('carry-forward-btn')).toBeVisible();
    await page.getByTestId('carry-forward-btn').click();
    await expect(page.getByTestId('carry-forward-dialog')).toBeVisible();
    await page.getByTestId('carry-confirm').click();

    await expect(page.getByTestId('plan-state')).toContainText('Carry Forward');
  });
});

test.describe('Manager Dashboard', () => {
  test('[SMOKE] Manager dashboard loads with team data', async ({ page }) => {
    await installMockApi(page);

    await page.goto('/');

    await page.getByTestId('nav-team-dashboard').click();
    await expect(page.getByTestId('team-dashboard-page')).toBeVisible();
    await expect(page.getByTestId('team-summary-grid')).toBeVisible();
    await expect(page.getByTestId(`team-row-${REPORT_USER_ID}`)).toBeVisible();
  });
});

test.describe('Lock Rejection', () => {
  test('[FULL] Lock is rejected when validation fails', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan(),
      commits: [
        buildCommit({
          id: 'commit-invalid',
          chessPriority: 'QUEEN',
          validationErrors: [
            {
              code: 'MISSING_RCDO_OR_REASON',
              message: 'Link to an outcome or provide a non-strategic reason.',
            },
          ],
        }),
        buildCommit({
          id: 'commit-invalid-2',
          chessPriority: 'QUEEN',
          validationErrors: [],
        }),
        buildCommit({
          id: 'commit-invalid-3',
          chessPriority: 'QUEEN',
          validationErrors: [],
        }),
      ],
    });

    await page.goto('/');

    await expect(page.getByTestId('validation-panel')).toBeVisible();
    await expect(page.getByTestId('validation-panel')).toContainText('validation errors');
    await expect(page.getByTestId('validation-panel')).toContainText('QUEEN commits — max 2 allowed');
  });
});

test.describe('Late Lock Path', () => {
  test('[FULL] Late lock badge is shown when reconciliation starts after the week closes', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({
        weekStartDate: mondayIso(-1),
        state: 'RECONCILING',
        lockType: 'LATE_LOCK',
        lockedAt: now,
      }),
      commits: [buildCommit()],
    });

    await page.goto('/');

    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId('plan-late-lock')).toBeVisible();
  });
});

test.describe('Permission Denial', () => {
  test('[FULL] Manager drill-down surfaces a permission error cleanly', async ({ page }) => {
    await installMockApi(page, { denyManagerDrillDown: true });

    await page.goto('/');
    await page.getByTestId('nav-team-dashboard').click();
    await expect(page.getByTestId('team-summary-grid')).toBeVisible();

    await page.getByTestId(`drill-down-${REPORT_USER_ID}`).click();
    await expect(page.getByTestId('drilldown-error')).toContainText('Access denied');
  });
});
