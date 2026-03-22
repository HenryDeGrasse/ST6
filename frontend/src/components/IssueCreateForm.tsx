import React, { useEffect, useMemo, useState } from "react";
import type {
  CreateIssueRequest,
  Team,
  TeamDetailResponse,
  TeamMember,
  Issue,
  IssueListResponse,
  EffortType,
  SuggestEffortTypeResponse,
} from "@weekly-commitments/contracts";
import { ChessPriority } from "@weekly-commitments/contracts";
import { EffortTypePicker } from "./EffortTypePicker.js";
import { ChessPicker } from "./ChessPicker.js";
import { RcdoPicker } from "./RcdoPicker.js";
import { useApiClient } from "../api/ApiContext.js";
import { useRcdo } from "../hooks/useRcdo.js";
import styles from "./IssueCreateForm.module.css";

export interface IssueCreateFormProps {
  teams: Team[];
  defaultTeamId?: string;
  onSubmit: (teamId: string, req: CreateIssueRequest) => Promise<void>;
  onCancel: () => void;
  submitting?: boolean;
}

interface FormState {
  title: string;
  description: string;
  teamId: string;
  outcomeId: string | null;
  effortType: EffortType | null;
  estimatedHours: string;
  chessPriority: ChessPriority | null;
  assigneeUserId: string;
  blockedByIssueId: string;
  blockedByIssueQuery: string;
}

interface FormErrors {
  title?: string;
  teamId?: string;
}

function formatEffortTypeLabel(value: EffortType): string {
  return value.charAt(0) + value.slice(1).toLowerCase();
}

function memberLabel(member: TeamMember): string {
  return `${member.userId}${member.role === "OWNER" ? " (Owner)" : ""}`;
}

/**
 * Form for creating a new backlog issue.
 *
 * Includes AI effort-type suggestion, RCDO outcome linking, assignee selection,
 * and a blocked-by issue search scoped to the selected team.
 */
export const IssueCreateForm: React.FC<IssueCreateFormProps> = ({
  teams,
  defaultTeamId,
  onSubmit,
  onCancel,
  submitting = false,
}) => {
  const client = useApiClient();
  const { tree, searchResults, fetchTree, search, clearSearch } = useRcdo();

  const [form, setForm] = useState<FormState>({
    title: "",
    description: "",
    teamId: defaultTeamId ?? teams[0]?.id ?? "",
    outcomeId: null,
    effortType: null,
    estimatedHours: "",
    chessPriority: null,
    assigneeUserId: "",
    blockedByIssueId: "",
    blockedByIssueQuery: "",
  });

  const [errors, setErrors] = useState<FormErrors>({});
  const [aiSuggestion, setAiSuggestion] = useState<EffortType | null>(null);
  const [aiLoading, setAiLoading] = useState(false);
  const [teamMembers, setTeamMembers] = useState<TeamMember[]>([]);
  const [availableIssues, setAvailableIssues] = useState<Issue[]>([]);

  useEffect(() => {
    void fetchTree();
  }, [fetchTree]);

  useEffect(() => {
    if (!form.teamId && teams.length > 0) {
      setForm((prev) => ({ ...prev, teamId: defaultTeamId ?? teams[0].id }));
    }
  }, [teams, defaultTeamId, form.teamId]);

  useEffect(() => {
    if (!form.teamId) {
      setTeamMembers([]);
      setAvailableIssues([]);
      return;
    }

    let cancelled = false;

    const loadTeamContext = async () => {
      try {
        const [teamDetailResp, issuesResp] = await Promise.all([
          client.GET("/teams/{teamId}", {
            params: { path: { teamId: form.teamId } },
          }),
          client.GET("/teams/{teamId}/issues", {
            params: {
              path: { teamId: form.teamId },
              query: { page: 0, size: 100 },
            },
          }),
        ]);

        if (!cancelled) {
          const teamDetail = teamDetailResp.data as TeamDetailResponse | undefined;
          const issues = issuesResp.data as IssueListResponse | undefined;
          setTeamMembers(teamDetail?.members ?? []);
          setAvailableIssues(issues?.content ?? []);
        }
      } catch {
        if (!cancelled) {
          setTeamMembers([]);
          setAvailableIssues([]);
        }
      }
    };

    void loadTeamContext();

    return () => {
      cancelled = true;
    };
  }, [client, form.teamId]);

  const blockerOptions = useMemo(
    () =>
      availableIssues.map((issue) => ({
        id: issue.id,
        label: `${issue.issueKey} — ${issue.title}`,
      })),
    [availableIssues],
  );

  useEffect(() => {
    if (!form.blockedByIssueId) {
      return;
    }

    const selectedBlocker = blockerOptions.find((option) => option.id === form.blockedByIssueId);
    if (!selectedBlocker) {
      setForm((prev) => ({ ...prev, blockedByIssueId: "", blockedByIssueQuery: "" }));
    }
  }, [blockerOptions, form.blockedByIssueId]);

  const fetchAiSuggestion = async () => {
    if (!form.title.trim()) return;
    setAiLoading(true);
    try {
      const resp = await client.POST("/ai/suggest-effort-type", {
        body: {
          title: form.title,
          description: form.description || undefined,
        },
      });
      if (resp.data) {
        const data = resp.data as SuggestEffortTypeResponse;
        if (data.status === "ok" && data.suggestedType) {
          setAiSuggestion(data.suggestedType as EffortType);
        }
      }
    } catch {
      // AI suggestion is best-effort; ignore errors
    } finally {
      setAiLoading(false);
    }
  };

  const validate = (): boolean => {
    const errs: FormErrors = {};
    if (!form.title.trim()) errs.title = "Title is required";
    if (!form.teamId) errs.teamId = "Team is required";
    setErrors(errs);
    return Object.keys(errs).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!validate()) return;

    const req: CreateIssueRequest = {
      title: form.title.trim(),
      description: form.description.trim() || null,
      outcomeId: form.outcomeId,
      effortType: form.effortType ?? null,
      estimatedHours: form.estimatedHours ? parseFloat(form.estimatedHours) : null,
      chessPriority: form.chessPriority ?? null,
      assigneeUserId: form.assigneeUserId || null,
      blockedByIssueId: form.blockedByIssueId || null,
    };

    await onSubmit(form.teamId, req);
  };

  const setField = <K extends keyof FormState>(key: K, value: FormState[K]) => {
    setForm((prev) => ({ ...prev, [key]: value }));
    if (key in errors) {
      setErrors((prev) => ({ ...prev, [key]: undefined }));
    }
  };

  return (
    <form
      onSubmit={handleSubmit}
      className={styles.form}
      data-testid="issue-create-form"
      noValidate
    >
      <div className={styles.fieldGroup}>
        <label className={`${styles.label} ${styles.labelRequired}`} htmlFor="issue-title">
          Title
        </label>
        <input
          id="issue-title"
          type="text"
          className="wc-input"
          value={form.title}
          onChange={(e) => setField("title", e.target.value)}
          onBlur={fetchAiSuggestion}
          disabled={submitting}
          placeholder="What needs to be done?"
          data-testid="issue-title-input"
          required
        />
        {errors.title && <span className={styles.errorText}>{errors.title}</span>}
      </div>

      <div className={styles.fieldGroup}>
        <label className={styles.label} htmlFor="issue-description">
          Description
        </label>
        <textarea
          id="issue-description"
          className={`wc-input ${styles.textarea}`}
          value={form.description}
          onChange={(e) => setField("description", e.target.value)}
          onBlur={fetchAiSuggestion}
          disabled={submitting}
          placeholder="Provide more detail…"
          data-testid="issue-description-input"
        />
      </div>

      {teams.length > 1 && (
        <div className={styles.fieldGroup}>
          <label className={`${styles.label} ${styles.labelRequired}`} htmlFor="issue-team">
            Team
          </label>
          <select
            id="issue-team"
            className="wc-select"
            value={form.teamId}
            onChange={(e) => setField("teamId", e.target.value)}
            disabled={submitting}
            data-testid="issue-team-select"
          >
            <option value="">Select team…</option>
            {teams.map((t) => (
              <option key={t.id} value={t.id}>
                {t.name}
              </option>
            ))}
          </select>
          {errors.teamId && <span className={styles.errorText}>{errors.teamId}</span>}
        </div>
      )}

      <div className={styles.fieldGroup}>
        <label className={styles.label}>Outcome Link</label>
        <RcdoPicker
          value={form.outcomeId}
          onChange={(selection) => setField("outcomeId", selection?.outcomeId ?? null)}
          tree={tree}
          searchResults={searchResults}
          onSearch={(query) => {
            void search(query);
          }}
          onClearSearch={clearSearch}
          disabled={submitting}
        />
      </div>

      <div className={styles.fieldGroup}>
        <label className={styles.label}>Effort Type</label>
        <EffortTypePicker
          value={form.effortType}
          onChange={(et) => setField("effortType", et)}
          aiSuggestion={aiSuggestion}
          disabled={submitting}
        />
        {aiLoading && <span className={styles.aiHint}>AI is suggesting an effort type…</span>}
        {aiSuggestion && !form.effortType && (
          <span className={styles.aiHint}>AI suggests: {formatEffortTypeLabel(aiSuggestion)}</span>
        )}
      </div>

      <div className={styles.row}>
        <div className={styles.fieldGroup}>
          <label className={styles.label} htmlFor="issue-hours">
            Estimated Hours
          </label>
          <input
            id="issue-hours"
            type="number"
            min="0"
            step="0.5"
            className="wc-input"
            value={form.estimatedHours}
            onChange={(e) => setField("estimatedHours", e.target.value)}
            disabled={submitting}
            placeholder="e.g. 4"
            data-testid="issue-hours-input"
          />
        </div>

        <div className={styles.fieldGroup}>
          <label className={styles.label}>Chess Priority</label>
          <ChessPicker
            value={form.chessPriority}
            onChange={(p) => setField("chessPriority", p)}
            disabled={submitting}
          />
        </div>
      </div>

      <div className={styles.row}>
        <div className={styles.fieldGroup}>
          <label className={styles.label} htmlFor="issue-assignee">
            Assignee
          </label>
          <select
            id="issue-assignee"
            className="wc-select"
            value={form.assigneeUserId}
            onChange={(e) => setField("assigneeUserId", e.target.value)}
            disabled={submitting}
            data-testid="issue-assignee-select"
          >
            <option value="">Unassigned</option>
            {teamMembers.map((member) => (
              <option key={member.userId} value={member.userId}>
                {memberLabel(member)}
              </option>
            ))}
          </select>
        </div>

        <div className={styles.fieldGroup}>
          <label className={styles.label} htmlFor="issue-blocked-by">
            Blocked By
          </label>
          <input
            id="issue-blocked-by"
            type="text"
            className="wc-input"
            list="issue-blocked-by-options"
            value={form.blockedByIssueQuery}
            onChange={(e) => {
              const query = e.target.value;
              const selected = blockerOptions.find((option) => option.label === query);
              setForm((prev) => ({
                ...prev,
                blockedByIssueQuery: query,
                blockedByIssueId: selected?.id ?? "",
              }));
            }}
            disabled={submitting}
            placeholder="Search issue key or title…"
            data-testid="issue-blocked-by-input"
          />
          <datalist id="issue-blocked-by-options">
            {blockerOptions.map((option) => (
              <option key={option.id} value={option.label} />
            ))}
          </datalist>
          <span className={styles.helpText}>
            Search within the selected team backlog to link a blocker.
          </span>
        </div>
      </div>

      <div className={styles.actions}>
        <button
          type="button"
          className="wc-button-secondary"
          onClick={onCancel}
          disabled={submitting}
          data-testid="issue-create-cancel"
        >
          Cancel
        </button>
        <button
          type="submit"
          className="wc-button"
          disabled={submitting}
          data-testid="issue-create-submit"
        >
          {submitting ? "Creating…" : "Create Issue"}
        </button>
      </div>
    </form>
  );
};
