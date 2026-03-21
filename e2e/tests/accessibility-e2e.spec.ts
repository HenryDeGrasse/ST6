/**
 * Playwright + axe-core accessibility (a11y) E2E tests.
 *
 * PRD §13.1: Automated a11y audits must report 0 critical/serious violations.
 *
 * These tests navigate to key application pages in a browser and run
 * @axe-core/playwright to detect WCAG violations that can only be caught
 * with full rendering, computed styles, focus management, and dynamic
 * interactions — things that unit-level jest-axe tests cannot cover.
 *
 * Test categories:
 *   [A11Y] = accessibility-only, runs in nightly E2E (NOT in PR smoke Gate 7)
 *
 * Tagged non-SMOKE: these run during the nightly E2E workflow (step-11),
 * not on every PR, because they add meaningful execution time and require
 * the full frontend to render complex UI states.
 *
 * Pages covered (per PRD §13.1 requirement):
 *   1. IC plan view      – Weekly Commitments plan editor (DRAFT state)
 *   2. Manager dashboard – Team summary grid with direct-reports list
 *   3. Reconciliation    – ReconciliationView in RECONCILING plan state
 */
import { test, expect } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import {
  installMockApi,
  buildPlan,
  buildCommit,
  mondayIso,
  MOCK_COMMIT_ID,
  MOCK_REPORT_USER_ID,
} from './helpers';

// ─── Shared constants ───────────────────────────────────────────────────────

const now = '2026-03-12T12:00:00Z';

// Only critical and serious violations fail the assertion per PRD §13.1.
const CRITICAL_SERIOUS_IMPACTS: string[] = ['critical', 'serious'];

/**
 * Asserts that the page contains 0 critical/serious axe violations.
 * Minor and moderate violations are intentionally excluded per PRD §13.1.
 */
async function assertNoA11yViolations(
  builder: AxeBuilder,
  label: string,
): Promise<void> {
  const results = await builder.analyze();
  const violations = results.violations.filter(
    (v) => v.impact != null && CRITICAL_SERIOUS_IMPACTS.includes(v.impact),
  );
  if (violations.length > 0) {
    const summary = violations
      .map(
        (v) =>
          `[${v.impact ?? 'unknown'}] ${v.id}: ${v.description}\n` +
          v.nodes.map((n) => `  - ${n.html}`).join('\n'),
      )
      .join('\n\n');
    throw new Error(
      `[${label}] Expected 0 critical/serious a11y violations but found ${violations.length}:\n\n${summary}`,
    );
  }
}

// ─── 1. IC Plan View (DRAFT state) ─────────────────────────────────────────

test.describe('a11y: IC plan view', () => {
  test('[A11Y] IC plan editor (DRAFT) has no critical/serious a11y violations', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({ state: 'DRAFT' }),
      commits: [
        buildCommit({
          id: MOCK_COMMIT_ID,
          chessPriority: 'KING',
          category: 'DELIVERY',
          validationErrors: [],
        }),
      ],
    });

    await page.goto('/');

    // Wait for plan content to fully render
    await expect(page.getByTestId('plan-header')).toBeVisible();
    await expect(page.getByTestId(`commit-row-${MOCK_COMMIT_ID}`)).toBeVisible();

    await assertNoA11yViolations(
      new AxeBuilder({ page })
        // Exclude the canvas pixel background — it has no semantic role and is
        // intentionally decorative; axe false-positives on canvas elements.
        .exclude('[data-testid="pixel-background"]'),
      'IC plan view',
    );
  });
});

// ─── 2. Manager Dashboard ───────────────────────────────────────────────────

test.describe('a11y: manager dashboard', () => {
  test('[A11Y] manager team-summary dashboard has no critical/serious a11y violations', async ({ page }) => {
    await installMockApi(page, {
      // Manager's own plan (LOCKED so Dashboard tab is always accessible)
      initialPlan: buildPlan({
        ownerUserId: '00000000-0000-0000-0000-000000000101',
        state: 'LOCKED',
        lockType: 'ON_TIME',
        lockedAt: now,
      }),
      teamSummaryUsers: [
        {
          userId: MOCK_REPORT_USER_ID,
          displayName: 'Alice Smith',
          planId: '10000000-0000-0000-0000-000000000999',
          state: 'RECONCILED',
          reviewStatus: 'REVIEW_PENDING',
          commitCount: 3,
          incompleteCount: 0,
          issueCount: 0,
          nonStrategicCount: 0,
          kingCount: 1,
          queenCount: 1,
          lastUpdated: now,
          isStale: false,
          isLateLock: false,
        },
      ],
    });

    await page.goto('/');

    // The host-stub default persona is Carol (MANAGER), so the host-level
    // Dashboard tab is rendered. Click the host nav explicitly to avoid the
    // frontend's own "Team Dashboard" button matching the same role query.
    await expect(page.getByTestId('pa-host-shell')).toBeVisible();
    await page.getByTestId('host-nav-dashboard').click();

    // Wait for the team summary grid to render with at least one row
    await expect(page.getByTestId(`team-row-${MOCK_REPORT_USER_ID}`)).toBeVisible({
      timeout: 10_000,
    });

    await assertNoA11yViolations(
      new AxeBuilder({ page }).exclude('[data-testid="pixel-background"]'),
      'Manager dashboard',
    );
  });
});

// ─── 3. Reconciliation View ─────────────────────────────────────────────────

test.describe('a11y: reconciliation view', () => {
  test('[A11Y] reconciliation view (RECONCILING) has no critical/serious a11y violations', async ({ page }) => {
    await installMockApi(page, {
      initialPlan: buildPlan({
        state: 'RECONCILING',
        lockType: 'ON_TIME',
        lockedAt: now,
      }),
      commits: [
        buildCommit({
          id: MOCK_COMMIT_ID,
          chessPriority: 'KING',
          category: 'DELIVERY',
          validationErrors: [],
        }),
      ],
    });

    await page.goto('/');

    // Reconciliation view renders automatically for RECONCILING plans
    await expect(page.getByTestId('reconciliation-view')).toBeVisible({ timeout: 10_000 });
    await expect(page.getByTestId(`reconcile-commit-${MOCK_COMMIT_ID}`)).toBeVisible();

    await assertNoA11yViolations(
      new AxeBuilder({ page }).exclude('[data-testid="pixel-background"]'),
      'Reconciliation view',
    );
  });
});
