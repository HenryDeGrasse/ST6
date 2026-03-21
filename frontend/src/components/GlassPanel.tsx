import React from "react";
import styles from "./GlassPanel.module.css";

// ─── Props ─────────────────────────────────────────────────────────────────────

export interface GlassPanelProps {
  /** Content rendered inside the panel. */
  children: React.ReactNode;
  /**
   * Additional CSS class(es) merged with the panel's module styles.
   * Use this for layout overrides (width, margin, etc.) at call-sites.
   */
  className?: string;
  /**
   * Visual intensity.
   * - `'default'`  — standard glassmorphism (shadow-md, glass-blur).
   * - `'elevated'` — stronger shadow and slightly more opaque background.
   * @default 'default'
   */
  variant?: "default" | "elevated";
  /**
   * Inline padding override.
   * Falls back to the panel's default (none / controlled by children) when omitted.
   * Accepts any valid CSS padding value, e.g. `"1rem"` or `"0.5rem 1rem"`.
   */
  padding?: string;
  /**
   * Underlying HTML element to render.
   * Defaults to `"div"`. Use `"section"`, `"article"`, `"aside"`, etc.
   * for semantic correctness.
   * @default 'div'
   */
  as?: keyof JSX.IntrinsicElements;
}

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * GlassPanel
 *
 * A reusable glassmorphism container that applies:
 * - `backdrop-filter: blur(var(--wc-glass-blur))` (when supported)
 * - Semi-transparent background via `var(--wc-glass-bg)`
 * - Subtle border via `var(--wc-glass-border)`
 * - `border-radius: var(--wc-radius-lg)`
 * - `box-shadow` scaling with the `variant` prop
 *
 * Browsers that don't support `backdrop-filter` (via the `@supports` rule in
 * the CSS module) automatically receive a more opaque solid background so
 * content remains legible.
 *
 * @example
 * // Basic usage
 * <GlassPanel padding="1rem">
 *   <p>Content here</p>
 * </GlassPanel>
 *
 * @example
 * // Elevated card with custom element + external class
 * <GlassPanel as="section" variant="elevated" className={styles.myCard}>
 *   <h2>Title</h2>
 * </GlassPanel>
 */
export const GlassPanel: React.FC<GlassPanelProps> = ({
  children,
  className,
  variant = "default",
  padding,
  as: Tag = "div",
}) => {
  const panelClasses = [styles.panel, variant === "elevated" ? styles.elevated : undefined, className]
    .filter(Boolean)
    .join(" ");

  const inlineStyle: React.CSSProperties | undefined = padding !== undefined ? { padding } : undefined;

  return (
    <Tag data-testid="glass-panel" data-variant={variant} className={panelClasses} style={inlineStyle}>
      {children}
    </Tag>
  );
};
