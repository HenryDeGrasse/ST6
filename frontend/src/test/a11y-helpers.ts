/**
 * Accessibility testing helpers using axe-core.
 *
 * PRD §13.1 requires automated a11y audits with a target of
 * zero critical/serious violations.
 *
 * TODO (follow-up): Add full-page a11y testing via Playwright +
 * @axe-core/playwright to the E2E suite for end-to-end coverage
 * including computed styles, focus management, and dynamic interactions.
 */
import { axe } from "jest-axe";

/**
 * Runs axe-core against the given container and asserts there are no
 * critical or serious accessibility violations.
 *
 * Minor and moderate violations are intentionally excluded from this
 * assertion per PRD §13.1 which targets zero critical/serious violations.
 *
 * @param container - The DOM element to audit (typically the `container`
 *   returned by `@testing-library/react`'s `render()`).
 * @throws If any critical or serious violations are found, with a
 *   human-readable summary of each violation and its affected nodes.
 */
export async function expectNoA11yViolations(container: HTMLElement): Promise<void> {
  const results = await axe(container);

  const seriousViolations = results.violations.filter((v) => v.impact === "critical" || v.impact === "serious");

  if (seriousViolations.length > 0) {
    const messages = seriousViolations
      .map(
        (v) =>
          `[${v.impact ?? "unknown"}] ${v.id}: ${v.description}\n` + v.nodes.map((n) => `  - ${n.html}`).join("\n"),
      )
      .join("\n\n");

    throw new Error(
      `Expected 0 critical/serious a11y violations but found ${seriousViolations.length}:\n\n${messages}`,
    );
  }
}
