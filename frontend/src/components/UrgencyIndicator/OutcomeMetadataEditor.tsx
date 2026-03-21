import React, { useState, useEffect, useCallback, useRef } from "react";
import type {
  OutcomeMetadataResponse,
  OutcomeMetadataRequest,
  ProgressUpdateRequest,
  OutcomeProgressType,
} from "../../hooks/useOutcomeMetadata.js";
import { UrgencyBadge } from "./UrgencyBadge.js";
import styles from "./OutcomeMetadataEditor.module.css";

// ─── Types ─────────────────────────────────────────────────────────────────────

type MilestoneStatus = "DONE" | "IN_PROGRESS" | "NOT_STARTED";

interface MilestoneRow {
  /** Stable identity for React reconciliation. Not sent to the backend. */
  uid: number;
  name: string;
  /** Relative weight for weighted completion percentage (0–100). */
  weight: number;
  status: MilestoneStatus;
}

// ─── Props ─────────────────────────────────────────────────────────────────────

export interface OutcomeMetadataEditorProps {
  /**
   * Outcome list from the RCDO tree — used to populate the outcome selector
   * dropdown.
   */
  outcomes: Array<{ outcomeId: string; outcomeName: string }>;
  /**
   * Existing metadata records for the org.  When null the form is still
   * rendered but starts with blank/default values.
   */
  metadata: OutcomeMetadataResponse[] | null;
  /**
   * Called with the full metadata payload when the user clicks Save.
   * Maps to PUT /api/v1/outcomes/{outcomeId}/metadata.
   */
  onSave: (outcomeId: string, data: OutcomeMetadataRequest) => Promise<void>;
  /**
   * Lightweight progress-only update handler available for the parent to
   * compose (e.g. inline current-value edits outside this form).
   * Maps to PATCH /api/v1/outcomes/{outcomeId}/progress.
   *
   * This prop is not used by the form's Save button directly — the form
   * always calls onSave with the full payload.
   */
  onUpdateProgress: (outcomeId: string, data: ProgressUpdateRequest) => Promise<void>;
  /** True while the parent is performing a network request. */
  loading: boolean;
}

// ─── Helpers ───────────────────────────────────────────────────────────────────

const MILESTONE_STATUSES: Array<{ value: MilestoneStatus; label: string }> = [
  { value: "NOT_STARTED", label: "Not Started" },
  { value: "IN_PROGRESS", label: "In Progress" },
  { value: "DONE", label: "Done" },
];

/**
 * Parse the raw milestone JSON string stored in OutcomeMetadataResponse into
 * a typed MilestoneRow array.  Unknown or malformed JSON returns an empty
 * array.  Each row gets a uid derived from the provided counter.
 */
function parseMilestones(json: string | null | undefined, getNextUid: () => number): MilestoneRow[] {
  if (!json) return [];
  try {
    const parsed: unknown = JSON.parse(json);
    if (!Array.isArray(parsed)) return [];
    return parsed.map((item) => {
      const m = item as Partial<Record<string, unknown>>;
      const rawStatus = m["status"];
      const status: MilestoneStatus =
        rawStatus === "DONE" || rawStatus === "IN_PROGRESS" || rawStatus === "NOT_STARTED"
          ? rawStatus
          : "NOT_STARTED";
      return {
        uid: getNextUid(),
        name: typeof m["name"] === "string" ? m["name"] : "",
        weight: typeof m["weight"] === "number" ? m["weight"] : 1,
        status,
      };
    });
  } catch {
    return [];
  }
}

/**
 * Serialise MilestoneRow[] to the raw JSON string expected by the backend.
 * Returns null when the array is empty.
 */
function serializeMilestones(rows: MilestoneRow[]): string | null {
  if (rows.length === 0) return null;
  return JSON.stringify(rows.map(({ name, weight, status }) => ({ name, weight, status })));
}

/**
 * Parse a controlled numeric input value into a finite number, or null when
 * the field is blank / invalid.
 */
function parseOptionalNumber(value: string): number | null {
  if (value.trim() === "") {
    return null;
  }

  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
}

// ─── Component ─────────────────────────────────────────────────────────────────

/**
 * OutcomeMetadataEditor
 *
 * Admin/manager form for configuring target dates and progress tracking on
 * RCDO outcomes.  The parent is responsible for role-gating (ADMIN or MANAGER
 * role required via useAuth).
 *
 * Features:
 * - Outcome selector dropdown (populated from RCDO tree)
 * - Target date picker
 * - Progress type radio selector (ACTIVITY | METRIC | MILESTONE)
 * - Conditional fields per progress type
 * - Add/remove milestone rows with name, weight, and status
 * - Read-only UrgencyBadge showing the current computed band
 * - Save button calling onSave with the full OutcomeMetadataRequest
 *
 * data-testid: 'outcome-metadata-editor'
 */
export const OutcomeMetadataEditor: React.FC<OutcomeMetadataEditorProps> = ({
  outcomes,
  metadata,
  onSave,
  // The onUpdateProgress prop is available for parent-level composition (e.g.
  // inline progress edits) but is not used by this form's main Save action.
  onUpdateProgress: _onUpdateProgress,
  loading,
}) => {
  // ── Stable UID counter for milestone rows ────────────────────────────────
  const nextUidRef = useRef(0);
  const getNextUid = useCallback(() => {
    nextUidRef.current += 1;
    return nextUidRef.current;
  }, []);

  // ── Form state ───────────────────────────────────────────────────────────
  const [selectedOutcomeId, setSelectedOutcomeId] = useState<string>("");
  const [targetDate, setTargetDate] = useState<string>("");
  const [progressType, setProgressType] = useState<OutcomeProgressType>("ACTIVITY");
  const [metricName, setMetricName] = useState<string>("");
  const [targetValue, setTargetValue] = useState<string>("");
  const [currentValue, setCurrentValue] = useState<string>("");
  const [unit, setUnit] = useState<string>("");
  const [milestones, setMilestones] = useState<MilestoneRow[]>([]);

  // ── Save feedback ────────────────────────────────────────────────────────
  const [saving, setSaving] = useState(false);
  const [successMsg, setSuccessMsg] = useState<string | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  const clearFeedback = useCallback(() => {
    setSuccessMsg(null);
    setErrorMsg(null);
  }, []);

  // ── Derived: current metadata for selected outcome ───────────────────────
  const currentMeta = metadata?.find((m) => m.outcomeId === selectedOutcomeId) ?? null;
  const urgencyBand = currentMeta?.urgencyBand ?? null;

  // ── Populate form when outcome selection changes ─────────────────────────
  useEffect(() => {
    if (!selectedOutcomeId) {
      setTargetDate("");
      setProgressType("ACTIVITY");
      setMetricName("");
      setTargetValue("");
      setCurrentValue("");
      setUnit("");
      setMilestones([]);
      return;
    }

    const meta = metadata?.find((m) => m.outcomeId === selectedOutcomeId);
    if (meta) {
      // Strip any time component from ISO datetime strings.
      setTargetDate(meta.targetDate ? meta.targetDate.split("T")[0] : "");
      setProgressType(meta.progressType ?? "ACTIVITY");
      setMetricName(meta.metricName ?? "");
      setTargetValue(meta.targetValue !== null && meta.targetValue !== undefined ? String(meta.targetValue) : "");
      setCurrentValue(meta.currentValue !== null && meta.currentValue !== undefined ? String(meta.currentValue) : "");
      setUnit(meta.unit ?? "");
      setMilestones(parseMilestones(meta.milestones, getNextUid));
    } else {
      // No existing metadata — start fresh.
      setTargetDate("");
      setProgressType("ACTIVITY");
      setMetricName("");
      setTargetValue("");
      setCurrentValue("");
      setUnit("");
      setMilestones([]);
    }
  }, [selectedOutcomeId, metadata, getNextUid]);

  // ── Milestone actions ────────────────────────────────────────────────────

  const handleAddMilestone = useCallback(() => {
    clearFeedback();
    const uid = getNextUid();
    setMilestones((prev) => [...prev, { uid, name: "", weight: 1, status: "NOT_STARTED" }]);
  }, [clearFeedback, getNextUid]);

  const handleRemoveMilestone = useCallback(
    (uid: number) => {
      clearFeedback();
      setMilestones((prev) => prev.filter((m) => m.uid !== uid));
    },
    [clearFeedback],
  );

  const handleMilestoneNameChange = useCallback(
    (uid: number, value: string) => {
      clearFeedback();
      setMilestones((prev) => prev.map((m) => (m.uid === uid ? { ...m, name: value } : m)));
    },
    [clearFeedback],
  );

  const handleMilestoneWeightChange = useCallback(
    (uid: number, value: string) => {
      clearFeedback();
      const parsed = parseFloat(value);
      const weight = Number.isFinite(parsed) ? parsed : 1;
      setMilestones((prev) => prev.map((m) => (m.uid === uid ? { ...m, weight } : m)));
    },
    [clearFeedback],
  );

  const handleMilestoneStatusChange = useCallback(
    (uid: number, value: string) => {
      clearFeedback();
      const status: MilestoneStatus =
        value === "DONE" || value === "IN_PROGRESS" || value === "NOT_STARTED" ? value : "NOT_STARTED";
      setMilestones((prev) => prev.map((m) => (m.uid === uid ? { ...m, status } : m)));
    },
    [clearFeedback],
  );

  // ── Save handler ─────────────────────────────────────────────────────────

  const handleSave = useCallback(async () => {
    if (!selectedOutcomeId) return;

    setSaving(true);
    setSuccessMsg(null);
    setErrorMsg(null);

    const data: OutcomeMetadataRequest = {
      targetDate: targetDate || null,
      progressType,
    };

    if (progressType === "METRIC") {
      data.metricName = metricName || null;
      data.targetValue = parseOptionalNumber(targetValue);
      data.currentValue = parseOptionalNumber(currentValue);
      data.unit = unit || null;
    } else if (progressType === "MILESTONE") {
      data.milestones = serializeMilestones(milestones);
    }

    try {
      await onSave(selectedOutcomeId, data);
      setSuccessMsg("Saved successfully.");
    } catch (e) {
      setErrorMsg(e instanceof Error ? e.message : "Save failed.");
    } finally {
      setSaving(false);
    }
  }, [
    selectedOutcomeId,
    targetDate,
    progressType,
    metricName,
    targetValue,
    currentValue,
    unit,
    milestones,
    onSave,
  ]);

  const isDisabled = loading || saving;
  const hasSelection = selectedOutcomeId !== "";

  // ── Render ───────────────────────────────────────────────────────────────

  return (
    <div data-testid="outcome-metadata-editor" className={styles.editor}>
      {/* ── Heading ── */}
      <h3 className={styles.title}>Outcome Metadata Editor</h3>
      <p className={styles.description}>
        Configure target dates and progress tracking for RCDO outcomes. Only visible to
        admin/manager roles.
      </p>

      {/* ── Outcome selector ── */}
      <div className={styles.fieldGroup}>
        <label className={styles.fieldLabel} htmlFor="ome-outcome-select">
          Outcome
        </label>
        <select
          id="ome-outcome-select"
          data-testid="ome-outcome-select"
          className={styles.select}
          value={selectedOutcomeId}
          onChange={(e) => {
            clearFeedback();
            setSelectedOutcomeId(e.target.value);
          }}
          disabled={isDisabled}
          aria-label="Select outcome to configure"
        >
          <option value="">— Select an outcome —</option>
          {outcomes.map(({ outcomeId, outcomeName }) => (
            <option key={outcomeId} value={outcomeId}>
              {outcomeName}
            </option>
          ))}
        </select>
        {outcomes.length === 0 && (
          <p className={styles.hintText}>No outcomes available. Ensure the RCDO tree is loaded.</p>
        )}
      </div>

      {/* ── Per-outcome form — only rendered once an outcome is selected ── */}
      {hasSelection && (
        <>
          {/* ── Target date ── */}
          <div className={styles.fieldGroup}>
            <label className={styles.fieldLabel} htmlFor="ome-target-date">
              Target Date
            </label>
            <input
              id="ome-target-date"
              data-testid="ome-target-date"
              type="date"
              className={styles.input}
              value={targetDate}
              onChange={(e) => {
                clearFeedback();
                setTargetDate(e.target.value);
              }}
              disabled={isDisabled}
              aria-label="Target date for this outcome"
            />
            <p className={styles.hintText}>
              Leave blank to track without a target date (urgency band will be{" "}
              <em>No Target</em>).
            </p>
          </div>

          {/* ── Progress type selector ── */}
          <fieldset className={styles.fieldGroup} disabled={isDisabled}>
            <legend className={styles.fieldLabel}>Progress Type</legend>
            <div className={styles.radioGroup} role="radiogroup" aria-label="Progress type">
              {(["ACTIVITY", "METRIC", "MILESTONE"] as OutcomeProgressType[]).map((type) => (
                <label
                  key={type}
                  className={styles.radioLabel}
                  data-testid={`ome-progress-type-${type.toLowerCase()}`}
                >
                  <input
                    type="radio"
                    name="ome-progress-type"
                    value={type}
                    checked={progressType === type}
                    onChange={() => {
                      clearFeedback();
                      setProgressType(type);
                    }}
                    className={styles.radioInput}
                    disabled={isDisabled}
                  />
                  <span className={styles.radioText}>
                    {type.charAt(0) + type.slice(1).toLowerCase()}
                  </span>
                </label>
              ))}
            </div>
          </fieldset>

          {/* ── ACTIVITY hint ── */}
          {progressType === "ACTIVITY" && (
            <p className={styles.hintText} data-testid="ome-activity-hint">
              Activity progress is derived automatically from weekly commits mapped to this outcome.
              No additional fields required.
            </p>
          )}

          {/* ── METRIC fields ── */}
          {progressType === "METRIC" && (
            <div className={styles.conditionalSection} data-testid="ome-metric-fields">
              <div className={styles.fieldRow}>
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="ome-metric-name">
                    Metric Name
                  </label>
                  <input
                    id="ome-metric-name"
                    data-testid="ome-metric-name"
                    type="text"
                    className={styles.input}
                    value={metricName}
                    onChange={(e) => {
                      clearFeedback();
                      setMetricName(e.target.value);
                    }}
                    placeholder="e.g. Revenue ($M)"
                    disabled={isDisabled}
                    aria-label="Metric name"
                  />
                </div>

                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="ome-unit">
                    Unit
                  </label>
                  <input
                    id="ome-unit"
                    data-testid="ome-unit"
                    type="text"
                    className={styles.input}
                    value={unit}
                    onChange={(e) => {
                      clearFeedback();
                      setUnit(e.target.value);
                    }}
                    placeholder="e.g. $M, %, pts"
                    disabled={isDisabled}
                    aria-label="Unit of measurement"
                  />
                </div>
              </div>

              <div className={styles.fieldRow}>
                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="ome-target-value">
                    Target Value
                  </label>
                  <input
                    id="ome-target-value"
                    data-testid="ome-target-value"
                    type="number"
                    className={styles.input}
                    value={targetValue}
                    onChange={(e) => {
                      clearFeedback();
                      setTargetValue(e.target.value);
                    }}
                    placeholder="0"
                    disabled={isDisabled}
                    aria-label="Target value"
                  />
                </div>

                <div className={styles.fieldGroup}>
                  <label className={styles.fieldLabel} htmlFor="ome-current-value">
                    Current Value
                  </label>
                  <input
                    id="ome-current-value"
                    data-testid="ome-current-value"
                    type="number"
                    className={styles.input}
                    value={currentValue}
                    onChange={(e) => {
                      clearFeedback();
                      setCurrentValue(e.target.value);
                    }}
                    placeholder="0"
                    disabled={isDisabled}
                    aria-label="Current value"
                  />
                </div>
              </div>
            </div>
          )}

          {/* ── MILESTONE fields ── */}
          {progressType === "MILESTONE" && (
            <div className={styles.conditionalSection} data-testid="ome-milestone-fields">
              <div className={styles.milestoneHeader}>
                <span className={styles.fieldLabel}>Milestones</span>
                <button
                  type="button"
                  data-testid="ome-add-milestone"
                  className={styles.addButton}
                  onClick={handleAddMilestone}
                  disabled={isDisabled}
                >
                  + Add Milestone
                </button>
              </div>

              {milestones.length === 0 ? (
                <p className={styles.hintText}>
                  No milestones yet. Click <strong>+ Add Milestone</strong> to begin tracking.
                </p>
              ) : (
                <>
                  {/* Column headers */}
                  <div className={styles.milestoneColHeaders} aria-hidden="true">
                    <span className={styles.milestoneColName}>Name</span>
                    <span className={styles.milestoneColWeight}>Weight</span>
                    <span className={styles.milestoneColStatus}>Status</span>
                  </div>

                  <div className={styles.milestoneList} role="list">
                    {milestones.map((milestone, index) => (
                      <div
                        key={milestone.uid}
                        className={styles.milestoneRow}
                        data-testid={`ome-milestone-row-${index}`}
                        role="listitem"
                      >
                        <input
                          type="text"
                          data-testid={`ome-milestone-name-${index}`}
                          className={`${styles.input} ${styles.milestoneNameInput}`}
                          value={milestone.name}
                          onChange={(e) => {
                            handleMilestoneNameChange(milestone.uid, e.target.value);
                          }}
                          placeholder="Milestone name"
                          disabled={isDisabled}
                          aria-label={`Milestone ${index + 1} name`}
                        />

                        <input
                          type="number"
                          data-testid={`ome-milestone-weight-${index}`}
                          className={`${styles.input} ${styles.milestoneWeightInput}`}
                          value={milestone.weight}
                          onChange={(e) => {
                            handleMilestoneWeightChange(milestone.uid, e.target.value);
                          }}
                          min={0}
                          max={100}
                          step={0.1}
                          placeholder="1"
                          disabled={isDisabled}
                          aria-label={`Milestone ${index + 1} weight`}
                        />

                        <select
                          data-testid={`ome-milestone-status-${index}`}
                          className={`${styles.select} ${styles.milestoneStatusSelect}`}
                          value={milestone.status}
                          onChange={(e) => {
                            handleMilestoneStatusChange(milestone.uid, e.target.value);
                          }}
                          disabled={isDisabled}
                          aria-label={`Milestone ${index + 1} status`}
                        >
                          {MILESTONE_STATUSES.map(({ value, label }) => (
                            <option key={value} value={value}>
                              {label}
                            </option>
                          ))}
                        </select>

                        <button
                          type="button"
                          data-testid={`ome-remove-milestone-${index}`}
                          className={styles.removeButton}
                          onClick={() => {
                            handleRemoveMilestone(milestone.uid);
                          }}
                          disabled={isDisabled}
                          aria-label={`Remove milestone ${index + 1}`}
                          title="Remove this milestone"
                        >
                          ×
                        </button>
                      </div>
                    ))}
                  </div>
                </>
              )}
            </div>
          )}

          {/* ── Current urgency band (read-only) ── */}
          {urgencyBand && (
            <div className={styles.urgencyRow} data-testid="ome-urgency-row">
              <span className={styles.fieldLabel}>Current Urgency</span>
              <UrgencyBadge urgencyBand={urgencyBand} size="md" />
            </div>
          )}

          {/* ── Feedback messages ── */}
          {errorMsg && (
            <div data-testid="ome-error" role="alert" className={styles.errorBox}>
              {errorMsg}
            </div>
          )}

          {successMsg && (
            <div data-testid="ome-success" role="status" className={styles.successMsg}>
              {successMsg}
            </div>
          )}

          {/* ── Actions ── */}
          <div className={styles.actions}>
            <button
              type="button"
              data-testid="ome-save-btn"
              className={styles.saveButton}
              onClick={() => {
                void handleSave();
              }}
              disabled={isDisabled}
            >
              {saving ? "Saving…" : "Save"}
            </button>
          </div>
        </>
      )}
    </div>
  );
};
