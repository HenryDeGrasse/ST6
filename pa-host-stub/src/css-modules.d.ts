/**
 * Type declarations for CSS Module files (*.module.css).
 *
 * Required because pa-host-stub's TypeScript compiler walks into
 * ../frontend/src when resolving @weekly-commitments/frontend, and
 * needs to recognize CSS Module imports (e.g. ThemeToggle.module.css).
 * The identical declaration lives in frontend/src/css-modules.d.ts;
 * this copy makes it available in the pa-host-stub compilation unit.
 */
declare module "*.module.css" {
  const classes: Record<string, string>;
  export default classes;
}
