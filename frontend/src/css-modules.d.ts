/**
 * Type declarations for CSS Module files (*.module.css).
 *
 * Allows TypeScript to treat CSS Module imports as Record<string, string>
 * without needing a separate per-file .d.ts.
 */
declare module "*.module.css" {
  const classes: Record<string, string>;
  export default classes;
}
