import React from "react";
import { ChessPriority } from "@weekly-commitments/contracts";

export interface ChessPickerProps {
  value: ChessPriority | null;
  onChange: (priority: ChessPriority) => void;
  disabled?: boolean;
}

const CHESS_LABELS: Record<ChessPriority, string> = {
  [ChessPriority.KING]: "♚ King – Must happen",
  [ChessPriority.QUEEN]: "♛ Queen – High leverage",
  [ChessPriority.ROOK]: "♜ Rook – Strong execution",
  [ChessPriority.BISHOP]: "♝ Bishop – Support/enablement",
  [ChessPriority.KNIGHT]: "♞ Knight – Exploration",
  [ChessPriority.PAWN]: "♟ Pawn – Small task/hygiene",
};

/**
 * Chess priority selector for a single commitment.
 */
export const ChessPicker: React.FC<ChessPickerProps> = ({
  value,
  onChange,
  disabled = false,
}) => {
  return (
    <select
      data-testid="chess-picker"
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value as ChessPriority)}
      disabled={disabled}
      aria-label="Chess priority"
    >
      <option value="">Select priority…</option>
      {Object.values(ChessPriority).map((p) => (
        <option key={p} value={p}>
          {CHESS_LABELS[p]}
        </option>
      ))}
    </select>
  );
};
