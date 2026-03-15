import React from "react";
import { CommitCategory } from "@weekly-commitments/contracts";

export interface CategoryPickerProps {
  value: CommitCategory | null;
  onChange: (category: CommitCategory) => void;
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

/**
 * Category dropdown for a commitment.
 */
export const CategoryPicker: React.FC<CategoryPickerProps> = ({
  value,
  onChange,
  disabled = false,
}) => {
  return (
    <select
      data-testid="category-picker"
      value={value ?? ""}
      onChange={(e) => onChange(e.target.value as CommitCategory)}
      disabled={disabled}
      aria-label="Category"
    >
      <option value="">Select category…</option>
      {Object.values(CommitCategory).map((c) => (
        <option key={c} value={c}>
          {CATEGORY_LABELS[c]}
        </option>
      ))}
    </select>
  );
};
