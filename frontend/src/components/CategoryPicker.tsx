import React from "react";
import { CommitCategory } from "@weekly-commitments/contracts";
import styles from "./CategoryPicker.module.css";

export interface CategoryPickerProps {
  value: CommitCategory | null;
  onChange: (category: CommitCategory | null) => void;
  disabled?: boolean;
}

const CATEGORY_LABELS: Record<CommitCategory, string> = {
  [CommitCategory.DELIVERY]: "Delivery",
  [CommitCategory.OPERATIONS]: "Operations",
  [CommitCategory.CUSTOMER]: "Customer",
  [CommitCategory.GTM]: "GTM",
  [CommitCategory.PEOPLE]: "People",
  [CommitCategory.LEARNING]: "Learning",
  [CommitCategory.TECH_DEBT]: "Tech Debt",
};

/** Maps a CommitCategory value to the matching dot CSS class. */
const CATEGORY_DOT_CLASS: Record<CommitCategory, string> = {
  [CommitCategory.DELIVERY]: styles.dotDelivery,
  [CommitCategory.OPERATIONS]: styles.dotOperations,
  [CommitCategory.CUSTOMER]: styles.dotCustomer,
  [CommitCategory.GTM]: styles.dotGtm,
  [CommitCategory.PEOPLE]: styles.dotPeople,
  [CommitCategory.LEARNING]: styles.dotLearning,
  [CommitCategory.TECH_DEBT]: styles.dotTechDebt,
};

/**
 * Category dropdown for a commitment.
 * Shows a colour-coded dot next to the select when a category is chosen.
 */
export const CategoryPicker: React.FC<CategoryPickerProps> = ({ value, onChange, disabled = false }) => {
  return (
    <div className={styles.wrapper}>
      <select
        data-testid="category-picker"
        value={value ?? ""}
        onChange={(e) => onChange(e.target.value ? (e.target.value as CommitCategory) : null)}
        disabled={disabled}
        aria-label="Category"
        className={[styles.select, value ? styles.selectActive : ""].join(" ").trim()}
      >
        <option value="">Select category…</option>
        {Object.values(CommitCategory).map((c) => (
          <option key={c} value={c}>
            {CATEGORY_LABELS[c]}
          </option>
        ))}
      </select>

      {/* Colour dot — only shown when a category is selected */}
      {value && (
        <span className={styles.dotWrapper} aria-hidden="true">
          <span className={[styles.dot, CATEGORY_DOT_CLASS[value]].join(" ")} />
        </span>
      )}
    </div>
  );
};
