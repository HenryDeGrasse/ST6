import "@testing-library/jest-dom/vitest";

/**
 * jsdom does not implement CanvasRenderingContext2D.
 *
 * The host shell mounts the frontend App through the exported module graph,
 * which includes PixelBackground. Mirror the frontend test environment by
 * returning null from getContext so canvas-based components gracefully no-op.
 */
Object.defineProperty(HTMLCanvasElement.prototype, "getContext", {
  value: () => null,
  writable: true,
  configurable: true,
});
