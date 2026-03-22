/**
 * PixelBackground – no-op stub.
 *
 * The previous animated chess-board canvas was purely decorative and conflicts
 * with the clean enterprise dashboard aesthetic. This component now renders
 * nothing. The exports are preserved so existing imports compile without
 * changes.
 */
import React from "react";

// ─── Public types (preserved for import compatibility) ─────────────────────────

export interface DataPoint {
  x: number;
  y: number;
  value?: number;
}

export interface PixelBackgroundProps {
  /**
   * Kept for API compatibility. Has no effect.
   */
  intensity?: number;
  /**
   * Kept for API compatibility. Has no effect.
   */
  dataPoints?: DataPoint[];
}

// ─── Component ─────────────────────────────────────────────────────────────────

export const PixelBackground: React.FC<PixelBackgroundProps> = (_props) => {
  return null;
};
