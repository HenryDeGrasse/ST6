/**
 * StatusIcon – SVG icon set for plan states and UI affordances.
 *
 * All icons use a 24×24 viewBox with stroke-based (outline) styling so they
 * inherit the current text colour via `stroke="currentColor"`. Use the parent
 * element's CSS `color` property to tint them.
 *
 * Supported icon names (see `StatusIconName`):
 *   pencil       – draft state / editing
 *   lock         – locked / read-only state
 *   arrows       – reconciling (double rotating arrows)
 *   check        – reconciled / done
 *   partial      – partial completion / in-progress diamond
 *   trash        – dropped / removed item
 *   return-arrow – carry-forward action
 *   loading      – spinner / pending work
 *   bell         – notifications
 *   warning      – validation warning
 *   error-x      – validation error
 *   target       – RCDO link
 *   pin          – non-strategic / pinned item
 *   robot        – AI-generated content
 */
import React from "react";

// ─── Types ─────────────────────────────────────────────────────────────────────

export type StatusIconName =
  | "pencil"
  | "lock"
  | "arrows"
  | "check"
  | "partial"
  | "trash"
  | "return-arrow"
  | "loading"
  | "bell"
  | "warning"
  | "error-x"
  | "target"
  | "pin"
  | "robot";

export interface StatusIconProps {
  /** Which icon to render. */
  icon: StatusIconName;
  /** Width and height in pixels. @default 20 */
  size?: number;
  /** Extra CSS class(es) applied to the root `<svg>` element. */
  className?: string;
}

// ─── Icon path definitions ─────────────────────────────────────────────────────

function IconPaths({ icon }: { icon: StatusIconName }): React.ReactElement {
  switch (icon) {
    // ── Pencil (draft / edit) ──────────────────────────────────────────────────
    case "pencil":
      return (
        <>
          <path d="M17 3a2.828 2.828 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5L17 3z" />
        </>
      );

    // ── Lock (locked state) ────────────────────────────────────────────────────
    case "lock":
      return (
        <>
          <rect x="5" y="11" width="14" height="11" rx="2" ry="2" />
          <path d="M17 11V7a5 5 0 0 0-10 0v4" />
        </>
      );

    // ── Arrows (reconciling / sync) ────────────────────────────────────────────
    case "arrows":
      return (
        <>
          {/* top arc, arrow pointing right */}
          <polyline points="23 4 23 10 17 10" />
          <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
        </>
      );

    // ── Check (reconciled / done) ──────────────────────────────────────────────
    case "check":
      return (
        <>
          <polyline points="20 6 9 17 4 12" />
        </>
      );

    // ── Partial (amber diamond) ────────────────────────────────────────────────
    case "partial":
      return (
        <>
          <polygon points="12 3 21 12 12 21 3 12" />
          <path d="M9 12h6" />
        </>
      );

    // ── Trash (dropped / removed) ──────────────────────────────────────────────
    case "trash":
      return (
        <>
          <path d="M3 6h18" />
          <path d="M8 6V4h8v2" />
          <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
          <line x1="10" y1="10" x2="10" y2="18" />
          <line x1="14" y1="10" x2="14" y2="18" />
        </>
      );

    // ── Return arrow (carry-forward) ───────────────────────────────────────────
    case "return-arrow":
      return (
        <>
          <polyline points="9 14 4 19 9 24" />
          <path d="M20 4v7a4 4 0 0 1-4 4H4" />
        </>
      );

    // ── Loading (spinner arc) ──────────────────────────────────────────────────
    case "loading":
      return (
        <>
          <path d="M21 12a9 9 0 1 1-9-9" />
        </>
      );

    // ── Bell (notifications) ───────────────────────────────────────────────────
    case "bell":
      return (
        <>
          <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
          <path d="M13.73 21a2 2 0 0 1-3.46 0" />
        </>
      );

    // ── Warning (validation warning) ──────────────────────────────────────────
    case "warning":
      return (
        <>
          <path d="M10.29 3.86L1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z" />
          <line x1="12" y1="9" x2="12" y2="13" />
          <line x1="12" y1="17" x2="12.01" y2="17" />
        </>
      );

    // ── Error X (validation error) ─────────────────────────────────────────────
    case "error-x":
      return (
        <>
          <circle cx="12" cy="12" r="10" />
          <line x1="15" y1="9" x2="9" y2="15" />
          <line x1="9" y1="9" x2="15" y2="15" />
        </>
      );

    // ── Target (RCDO link / bullseye) ──────────────────────────────────────────
    case "target":
      return (
        <>
          <circle cx="12" cy="12" r="10" />
          <circle cx="12" cy="12" r="6" />
          <circle cx="12" cy="12" r="2" />
        </>
      );

    // ── Pin (non-strategic / thumbtack) ───────────────────────────────────────
    case "pin":
      return (
        <>
          <path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z" />
          <circle cx="12" cy="10" r="3" />
        </>
      );

    // ── Robot (AI) ─────────────────────────────────────────────────────────────
    case "robot":
      return (
        <>
          {/* head */}
          <rect x="3" y="7" width="18" height="13" rx="2" />
          {/* antenna */}
          <line x1="12" y1="2" x2="12" y2="7" />
          <circle cx="12" cy="2" r="1" />
          {/* eyes */}
          <circle cx="8.5" cy="13" r="1.5" />
          <circle cx="15.5" cy="13" r="1.5" />
          {/* mouth */}
          <line x1="8.5" y1="16.5" x2="15.5" y2="16.5" />
          {/* ears */}
          <line x1="3" y1="11" x2="1" y2="11" />
          <line x1="3" y1="15" x2="1" y2="15" />
          <line x1="21" y1="11" x2="23" y2="11" />
          <line x1="21" y1="15" x2="23" y2="15" />
        </>
      );
  }
}

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * Renders the named status icon as an inline SVG.
 *
 * @example
 * // Show a lock icon at 16px in a span that colours it gold
 * <span style={{ color: 'var(--wc-color-accent)' }}>
 *   <StatusIcon icon="lock" size={16} />
 * </span>
 */
export const StatusIcon: React.FC<StatusIconProps> = ({
  icon,
  size = 20,
  className,
}) => (
  <svg
    data-testid={`status-icon-${icon}`}
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 24 24"
    width={size}
    height={size}
    fill="none"
    stroke="currentColor"
    strokeWidth="2"
    strokeLinecap="round"
    strokeLinejoin="round"
    aria-hidden="true"
    className={className}
  >
    <IconPaths icon={icon} />
  </svg>
);
