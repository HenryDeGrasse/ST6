import React from "react";
import { EffortType } from "@weekly-commitments/contracts";
import styles from "./EffortTypePicker.module.css";

export interface EffortTypePickerProps {
  value: EffortType | null;
  onChange: (effortType: EffortType | null) => void;
  /** AI-suggested effort type shown as a dimmed pre-selection. */
  aiSuggestion?: EffortType | null;
  disabled?: boolean;
}

const EFFORT_TYPE_LABELS: Record<EffortType, string> = {
  [EffortType.BUILD]: "Build",
  [EffortType.MAINTAIN]: "Maintain",
  [EffortType.COLLABORATE]: "Collaborate",
  [EffortType.LEARN]: "Learn",
};

const EFFORT_TYPE_CHIP_CLASS: Record<EffortType, string> = {
  [EffortType.BUILD]: styles.chipBuild,
  [EffortType.MAINTAIN]: styles.chipMaintain,
  [EffortType.COLLABORATE]: styles.chipCollaborate,
  [EffortType.LEARN]: styles.chipLearn,
};

/**
 * Chip-style effort type picker.
 *
 * Shows 4 chips (BUILD / MAINTAIN / COLLABORATE / LEARN).
 * Supports an optional AI suggestion that is shown in a dimmed/dashed state
 * until the user explicitly selects (confirms) or picks a different type
 * (override).
 */
export const EffortTypePicker: React.FC<EffortTypePickerProps> = ({
  value,
  onChange,
  aiSuggestion,
  disabled = false,
}) => {
  const handleClick = (et: EffortType) => {
    if (disabled) return;
    // Toggle off if already selected
    onChange(value === et ? null : et);
  };

  return (
    <div className={styles.wrapper} data-testid="effort-type-picker">
      {Object.values(EffortType).map((et) => {
        const isSelected = value === et;
        const isAiSuggested = !value && aiSuggestion === et;

        const classNames = [
          styles.chip,
          EFFORT_TYPE_CHIP_CLASS[et],
          isSelected ? styles.chipSelected : "",
          isAiSuggested ? styles.chipAiSuggested : "",
        ]
          .filter(Boolean)
          .join(" ");

        return (
          <button
            key={et}
            type="button"
            className={classNames}
            onClick={() => handleClick(et)}
            disabled={disabled}
            aria-pressed={isSelected}
            data-testid={`effort-type-chip-${et}`}
            title={isAiSuggested ? `AI suggests: ${EFFORT_TYPE_LABELS[et]}` : EFFORT_TYPE_LABELS[et]}
          >
            {EFFORT_TYPE_LABELS[et]}
            {isAiSuggested && <span className={styles.aiLabel}>AI</span>}
          </button>
        );
      })}

      {value && !disabled && (
        <button
          type="button"
          className={styles.clearBtn}
          onClick={() => onChange(null)}
          aria-label="Clear effort type"
          data-testid="effort-type-clear"
        >
          ✕
        </button>
      )}
    </div>
  );
};
