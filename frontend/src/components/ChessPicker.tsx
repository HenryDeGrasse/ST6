import React from "react";
import { ChessPriority } from "@weekly-commitments/contracts";
import { ChessIcon } from "./icons/ChessIcon.js";
import type { ChessPiece } from "./icons/ChessIcon.js";
import styles from "./ChessPicker.module.css";

export interface ChessPickerProps {
  value: ChessPriority | null;
  onChange: (priority: ChessPriority | null) => void;
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

/** Maps a ChessPriority to a ChessPiece for the icon. */
function priorityToPiece(priority: ChessPriority | null): ChessPiece | null {
  if (!priority) return null;
  const valid: ChessPiece[] = ["KING", "QUEEN", "ROOK", "BISHOP", "KNIGHT", "PAWN"];
  const upper = priority.toUpperCase() as ChessPiece;
  return valid.includes(upper) ? upper : null;
}

/**
 * Chess priority selector for a single commitment.
 * Shows a prominent chess piece icon when a priority is selected.
 */
export const ChessPicker: React.FC<ChessPickerProps> = ({ value, onChange, disabled = false }) => {
  const piece = priorityToPiece(value);

  return (
    <div className={styles.wrapper}>
      <select
        data-testid="chess-picker"
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value ? (e.target.value as ChessPriority) : null)}
        disabled={disabled}
        aria-label="Chess priority"
        className={[styles.select, piece ? styles.selectActive : ""].join(" ").trim()}
      >
        <option value="">Select priority…</option>
        {Object.values(ChessPriority).map((p) => (
          <option key={p} value={p}>
            {CHESS_LABELS[p]}
          </option>
        ))}
      </select>

      {/* Icon rendered after the select, sized to be clearly visible */}
      {piece && (
        <span className={styles.pieceIcon} aria-hidden="true">
          <ChessIcon piece={piece} size={20} />
        </span>
      )}
    </div>
  );
};
