import "@testing-library/jest-dom/vitest";

/**
 * Canvas mock for jsdom.
 *
 * jsdom does not implement the Canvas API. We explicitly mock
 * HTMLCanvasElement.prototype.getContext to return null so that
 * PixelBackground (and any other canvas-based components) gracefully
 * no-op in the test environment without throwing errors.
 *
 * All existing tests are unaffected because they rely on data-testid
 * selectors and never inspect canvas content.
 */
Object.defineProperty(HTMLCanvasElement.prototype, "getContext", {
  value: () => null,
  writable: true,
  configurable: true,
});
