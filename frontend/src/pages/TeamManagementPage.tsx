/**
 * TeamManagementPage
 *
 * Provides team detail editing (owner), member management (owner),
 * pending access-request approval/denial (owner), and a
 * "Request Access" button for non-members viewing a team.
 *
 * Also hosts the "Create Team" flow (manager-only).
 */
import React, { useCallback, useEffect, useState } from "react";
import type { Team } from "@weekly-commitments/contracts";
import { AccessRequestStatus, TeamRole } from "@weekly-commitments/contracts";
import { useTeamManagement } from "../hooks/useTeamManagement.js";
import { useAuth } from "../context/AuthContext.js";
import { ErrorBanner } from "../components/ErrorBanner.js";
import styles from "./TeamManagementPage.module.css";

// ── helpers ──────────────────────────────────────────────────────────────────

/** Derive a 2-5 char uppercase prefix from a team name. */
function derivePrefix(name: string): string {
  const words = name
    .trim()
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w.replace(/[^A-Za-z0-9]/g, ""));
  if (words.length === 0) return "";
  if (words.length === 1) return words[0].slice(0, 4).toUpperCase();
  return words
    .map((w) => w[0])
    .join("")
    .slice(0, 5)
    .toUpperCase();
}

function formatDate(iso: string): string {
  try {
    return new Date(iso).toLocaleDateString(undefined, {
      year: "numeric",
      month: "short",
      day: "numeric",
    });
  } catch {
    return iso;
  }
}

// ── Sub-components ────────────────────────────────────────────────────────────

interface CreateTeamModalProps {
  onClose: () => void;
  onCreate: (name: string, prefix: string, description: string) => Promise<void>;
  loading: boolean;
}

const CreateTeamModal: React.FC<CreateTeamModalProps> = ({ onClose, onCreate, loading }) => {
  const [name, setName] = useState("");
  const [prefix, setPrefix] = useState("");
  const [prefixOverridden, setPrefixOverridden] = useState(false);
  const [description, setDescription] = useState("");

  const handleNameChange = (value: string) => {
    setName(value);
    if (!prefixOverridden) {
      setPrefix(derivePrefix(value));
    }
  };

  const handlePrefixChange = (value: string) => {
    setPrefix(value.toUpperCase().replace(/[^A-Z0-9]/g, "").slice(0, 8));
    setPrefixOverridden(true);
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !prefix.trim()) return;
    await onCreate(name.trim(), prefix.trim(), description.trim());
  };

  return (
    <div
      className={styles.modalOverlay}
      onClick={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
      data-testid="create-team-modal"
    >
      <div className={styles.modal}>
        <div className={styles.modalHeader}>
          <span className={styles.modalTitle}>Create Team</span>
          <button
            type="button"
            className={styles.closeBtn}
            onClick={onClose}
            aria-label="Close"
          >
            ✕
          </button>
        </div>

        <form onSubmit={(e) => void handleSubmit(e)}>
          <div className={styles.formGroup}>
            <label className={styles.formLabel} htmlFor="team-name-input">
              Team name
            </label>
            <input
              id="team-name-input"
              className={styles.formInput}
              type="text"
              value={name}
              onChange={(e) => handleNameChange(e.target.value)}
              placeholder="e.g. Platform Engineering"
              required
              data-testid="create-team-name-input"
            />
          </div>

          <div className={styles.formGroup}>
            <label className={styles.formLabel} htmlFor="team-prefix-input">
              Issue key prefix
            </label>
            <input
              id="team-prefix-input"
              className={styles.formInput}
              type="text"
              value={prefix}
              onChange={(e) => handlePrefixChange(e.target.value)}
              placeholder="e.g. ENG"
              required
              maxLength={8}
              data-testid="create-team-prefix-input"
            />
            {prefix && (
              <span className={styles.prefixPreview} aria-live="polite">
                Issues will be keyed: {prefix}-1, {prefix}-2, …
              </span>
            )}
            <span className={styles.formHint}>Auto-derived from name; override if needed.</span>
          </div>

          <div className={styles.formGroup}>
            <label className={styles.formLabel} htmlFor="team-desc-input">
              Description (optional)
            </label>
            <textarea
              id="team-desc-input"
              className={styles.formInput}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="What does this team work on?"
              rows={2}
              data-testid="create-team-description-input"
            />
          </div>

          <div className={styles.formActions}>
            <button
              type="button"
              className="wc-button-secondary"
              onClick={onClose}
              disabled={loading}
              data-testid="create-team-cancel"
            >
              Cancel
            </button>
            <button
              type="submit"
              className="wc-button"
              disabled={loading || !name.trim() || !prefix.trim()}
              data-testid="create-team-submit"
            >
              {loading ? "Creating…" : "Create Team"}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

// ── Main page ─────────────────────────────────────────────────────────────────

export interface TeamManagementPageProps {
  /** If provided, open this team directly (skip team picker). */
  initialTeamId?: string;
  /** Callback to navigate back to the caller (BacklogPage, nav, etc.) */
  onBack?: () => void;
}

export const TeamManagementPage: React.FC<TeamManagementPageProps> = ({
  initialTeamId,
  onBack,
}) => {
  const { user } = useAuth();
  const hook = useTeamManagement();
  const isManager = user.roles.includes("MANAGER") || user.roles.includes("ADMIN");

  const [selectedTeamId, setSelectedTeamId] = useState<string>(initialTeamId ?? "");
  const [showCreateModal, setShowCreateModal] = useState(false);

  // Edit state for team detail
  const [editing, setEditing] = useState(false);
  const [editName, setEditName] = useState("");
  const [editDesc, setEditDesc] = useState("");

  // Add member form state
  const [addUserId, setAddUserId] = useState("");

  // Access request submitted state
  const [accessRequested, setAccessRequested] = useState(false);

  // Load teams on mount
  useEffect(() => {
    void hook.fetchTeams();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Auto-select first team if none set
  useEffect(() => {
    if (!selectedTeamId && hook.teams.length > 0) {
      setSelectedTeamId(hook.teams[0].id);
    }
  }, [hook.teams, selectedTeamId]);

  // Load detail & access requests when team changes
  useEffect(() => {
    if (!selectedTeamId) return;
    const ownerUserId = hook.teams.find((team) => team.id === selectedTeamId)?.ownerUserId;
    void hook.fetchTeamDetail(selectedTeamId);
    if (ownerUserId === user.userId) {
      void hook.fetchAccessRequests(selectedTeamId);
    }
    setAccessRequested(false);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [hook.teams, selectedTeamId, user.userId]);

  // When detail loads, sync edit fields
  useEffect(() => {
    if (hook.teamDetail?.team) {
      setEditName(hook.teamDetail.team.name);
      setEditDesc(hook.teamDetail.team.description ?? "");
      setEditing(false);
    }
  }, [hook.teamDetail]);

  const selectedTeam: Team | undefined = hook.teams.find((t) => t.id === selectedTeamId);
  const isOwner = selectedTeam?.ownerUserId === user.userId;
  const isMember =
    isOwner ||
    (hook.teamDetail?.members.some((m) => m.userId === user.userId) ?? false);

  const pendingRequests = hook.accessRequests.filter(
    (r) => r.status === AccessRequestStatus.PENDING,
  );

  const handleSaveTeam = useCallback(async () => {
    if (!selectedTeamId) return;
    const updated = await hook.updateTeam(selectedTeamId, {
      name: editName.trim() || undefined,
      description: editDesc.trim() || null,
    });
    if (updated) setEditing(false);
  }, [editDesc, editName, hook, selectedTeamId]);

  const handleAddMember = useCallback(async () => {
    if (!selectedTeamId || !addUserId.trim()) return;
    const member = await hook.addMember(selectedTeamId, {
      userId: addUserId.trim(),
      role: TeamRole.MEMBER,
    });
    if (member) setAddUserId("");
  }, [addUserId, hook, selectedTeamId]);

  const handleRemoveMember = useCallback(
    async (userId: string) => {
      if (!selectedTeamId) return;
      await hook.removeMember(selectedTeamId, userId);
    },
    [hook, selectedTeamId],
  );

  const handleApprove = useCallback(
    async (requestId: string) => {
      if (!selectedTeamId) return;
      await hook.decideAccessRequest(
        selectedTeamId,
        requestId,
        AccessRequestStatus.APPROVED,
      );
    },
    [hook, selectedTeamId],
  );

  const handleDeny = useCallback(
    async (requestId: string) => {
      if (!selectedTeamId) return;
      await hook.decideAccessRequest(
        selectedTeamId,
        requestId,
        AccessRequestStatus.DENIED,
      );
    },
    [hook, selectedTeamId],
  );

  const handleRequestAccess = useCallback(async () => {
    if (!selectedTeamId) return;
    const req = await hook.requestAccess(selectedTeamId);
    if (req) setAccessRequested(true);
  }, [hook, selectedTeamId]);

  const handleCreateTeam = useCallback(
    async (name: string, prefix: string, description: string) => {
      const team = await hook.createTeam({
        name,
        keyPrefix: prefix,
        description: description || null,
      });
      if (team) {
        setShowCreateModal(false);
        setSelectedTeamId(team.id);
      }
    },
    [hook],
  );

  return (
    <div className={styles.page} data-testid="team-management-page">
      {/* ── Page header ─────────────────────────────────────────────── */}
      <div className={styles.pageHeader}>
        <div style={{ display: "flex", alignItems: "center", gap: "0.75rem" }}>
          {onBack && (
            <button
              type="button"
              className={styles.backBtn}
              onClick={onBack}
              data-testid="team-mgmt-back-btn"
            >
              ← Back
            </button>
          )}
          <h1 className={styles.pageTitle}>Team Management</h1>
        </div>

        <div style={{ display: "flex", gap: "0.5rem", alignItems: "center" }}>
          {/* Team selector */}
          {hook.teams.length > 1 && (
            <select
              className="wc-select"
              value={selectedTeamId}
              onChange={(e) => setSelectedTeamId(e.target.value)}
              aria-label="Select team"
              data-testid="team-mgmt-team-select"
            >
              {hook.teams.map((t) => (
                <option key={t.id} value={t.id}>
                  {t.name}
                </option>
              ))}
            </select>
          )}

          {isManager && (
            <button
              type="button"
              className="wc-button"
              onClick={() => setShowCreateModal(true)}
              data-testid="create-team-btn"
            >
              + Create Team
            </button>
          )}
        </div>
      </div>

      {/* ── Errors ──────────────────────────────────────────────────── */}
      {hook.error && (
        <ErrorBanner message={hook.error} onDismiss={hook.clearError} />
      )}

      {/* ── Empty state ─────────────────────────────────────────────── */}
      {hook.teams.length === 0 && !hook.loading && (
        <div className={styles.emptyText} data-testid="team-mgmt-empty">
          {isManager
            ? "No teams yet. Create one to get started."
            : "You don't belong to any team yet."}
        </div>
      )}

      {/* ── Team detail section ──────────────────────────────────────── */}
      {selectedTeam && (
        <>
          {/* Team info */}
          <div className={styles.section} data-testid="team-info-section">
            <div className={styles.sectionHeader}>
              <span className={styles.sectionTitle}>Team Details</span>
              {isOwner && !editing && (
                <button
                  type="button"
                  className="wc-button-secondary"
                  onClick={() => setEditing(true)}
                  data-testid="edit-team-btn"
                >
                  Edit
                </button>
              )}
            </div>

            {editing ? (
              <div>
                <div className={styles.fieldGroup} style={{ marginBottom: "0.75rem" }}>
                  <label className={styles.fieldLabel} htmlFor="edit-team-name">
                    Name
                  </label>
                  <input
                    id="edit-team-name"
                    className={styles.fieldInput}
                    type="text"
                    value={editName}
                    onChange={(e) => setEditName(e.target.value)}
                    data-testid="edit-team-name-input"
                  />
                </div>
                <div className={styles.fieldGroup} style={{ marginBottom: "0.75rem" }}>
                  <label className={styles.fieldLabel} htmlFor="edit-team-desc">
                    Description
                  </label>
                  <textarea
                    id="edit-team-desc"
                    className={styles.fieldInput}
                    value={editDesc}
                    onChange={(e) => setEditDesc(e.target.value)}
                    rows={2}
                    data-testid="edit-team-desc-input"
                  />
                </div>
                <div style={{ display: "flex", gap: "0.5rem" }}>
                  <button
                    type="button"
                    className="wc-button"
                    onClick={() => void handleSaveTeam()}
                    disabled={hook.loading}
                    data-testid="save-team-btn"
                  >
                    {hook.loading ? "Saving…" : "Save"}
                  </button>
                  <button
                    type="button"
                    className="wc-button-secondary"
                    onClick={() => setEditing(false)}
                    data-testid="cancel-edit-team-btn"
                  >
                    Cancel
                  </button>
                </div>
              </div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "0.5rem" }}>
                <div className={styles.fieldRow}>
                  <div className={styles.fieldGroup}>
                    <span className={styles.fieldLabel}>Name</span>
                    <span className={styles.fieldValue} data-testid="team-name-display">
                      {selectedTeam.name}
                    </span>
                  </div>
                  <div className={styles.fieldGroup}>
                    <span className={styles.fieldLabel}>Key Prefix</span>
                    <span
                      className={styles.fieldValue}
                      style={{ fontFamily: "monospace", color: "var(--wc-color-accent, #C9A962)" }}
                      data-testid="team-prefix-display"
                    >
                      {selectedTeam.keyPrefix}
                    </span>
                  </div>
                </div>
                {selectedTeam.description && (
                  <div className={styles.fieldGroup}>
                    <span className={styles.fieldLabel}>Description</span>
                    <span className={styles.fieldValue} data-testid="team-desc-display">
                      {selectedTeam.description}
                    </span>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* ── Request access (non-member view) ───────────────────── */}
          {!isMember && (
            <div className={styles.section} data-testid="request-access-section">
              <div className={styles.sectionTitle}>Access</div>
              {accessRequested ? (
                <p className={styles.fieldValue} data-testid="access-request-sent">
                  Access request sent. An owner will review it shortly.
                </p>
              ) : (
                <div className={styles.requestAccessSection}>
                  <p className={styles.fieldValue}>
                    You are not a member of this team.
                  </p>
                  <button
                    type="button"
                    className={styles.requestAccessBtn}
                    onClick={() => void handleRequestAccess()}
                    disabled={hook.loading}
                    data-testid="request-access-btn"
                  >
                    Request Access
                  </button>
                </div>
              )}
            </div>
          )}

          {/* ── Members (members and owners) ─────────────────────────── */}
          {isMember && (
            <div className={styles.section} data-testid="members-section">
              <div className={styles.sectionHeader}>
                <span className={styles.sectionTitle}>
                  Members ({hook.teamDetail?.members.length ?? 0})
                </span>
              </div>

              {hook.loading && !hook.teamDetail ? (
                <p className={styles.loadingText}>Loading members…</p>
              ) : (
                <table className={styles.memberTable} data-testid="member-table">
                  <thead>
                    <tr>
                      <th>User ID</th>
                      <th>Role</th>
                      <th>Joined</th>
                      {isOwner && <th />}
                    </tr>
                  </thead>
                  <tbody>
                    {(hook.teamDetail?.members ?? []).map((member) => (
                      <tr key={member.userId} data-testid={`member-row-${member.userId}`}>
                        <td>{member.userId}</td>
                        <td>
                          <span
                            className={`${styles.roleBadge} ${
                              member.role === TeamRole.OWNER
                                ? styles.roleOwner
                                : styles.roleMember
                            }`}
                          >
                            {member.role}
                          </span>
                        </td>
                        <td>{formatDate(member.joinedAt)}</td>
                        {isOwner && (
                          <td>
                            {member.userId !== user.userId && (
                              <button
                                type="button"
                                className={styles.removeBtn}
                                onClick={() => void handleRemoveMember(member.userId)}
                                data-testid={`remove-member-${member.userId}`}
                                aria-label={`Remove ${member.userId}`}
                              >
                                Remove
                              </button>
                            )}
                          </td>
                        )}
                      </tr>
                    ))}
                    {(hook.teamDetail?.members.length ?? 0) === 0 && (
                      <tr>
                        <td colSpan={isOwner ? 4 : 3}>
                          <span className={styles.emptyText}>No members yet.</span>
                        </td>
                      </tr>
                    )}
                  </tbody>
                </table>
              )}

              {/* Add member form (owner only) */}
              {isOwner && (
                <div className={styles.addMemberForm} data-testid="add-member-form">
                  <input
                    className={styles.addMemberInput}
                    type="text"
                    value={addUserId}
                    onChange={(e) => setAddUserId(e.target.value)}
                    placeholder="User ID to add…"
                    aria-label="User ID to add"
                    data-testid="add-member-input"
                    onKeyDown={(e) => {
                      if (e.key === "Enter") {
                        e.preventDefault();
                        void handleAddMember();
                      }
                    }}
                  />
                  <button
                    type="button"
                    className="wc-button"
                    onClick={() => void handleAddMember()}
                    disabled={hook.loading || !addUserId.trim()}
                    data-testid="add-member-btn"
                  >
                    Add Member
                  </button>
                </div>
              )}
            </div>
          )}

          {/* ── Pending access requests (owner only) ─────────────────── */}
          {isOwner && (
            <div className={styles.section} data-testid="access-requests-section">
              <div className={styles.sectionHeader}>
                <span className={styles.sectionTitle}>
                  Access Requests{pendingRequests.length > 0 ? ` (${pendingRequests.length})` : ""}
                </span>
              </div>

              {hook.loadingRequests ? (
                <p className={styles.loadingText}>Loading requests…</p>
              ) : pendingRequests.length === 0 ? (
                <p className={styles.emptyText} data-testid="no-access-requests">
                  No pending access requests.
                </p>
              ) : (
                <div>
                  {pendingRequests.map((req) => (
                    <div key={req.id} className={styles.requestRow} data-testid={`access-request-${req.id}`}>
                      <span className={styles.requestUser}>{req.requesterUserId}</span>
                      <span className={styles.requestDate}>{formatDate(req.createdAt)}</span>
                      <span className={`${styles.requestStatus} ${styles.statusPending}`}>{req.status}</span>
                      <button
                        type="button"
                        className={styles.approveBtn}
                        onClick={() => void handleApprove(req.id)}
                        data-testid={`approve-request-${req.id}`}
                      >
                        Approve
                      </button>
                      <button
                        type="button"
                        className={styles.denyBtn}
                        onClick={() => void handleDeny(req.id)}
                        data-testid={`deny-request-${req.id}`}
                      >
                        Deny
                      </button>
                    </div>
                  ))}
                </div>
              )}
            </div>
          )}
        </>
      )}

      {/* ── Create team modal ──────────────────────────────────────── */}
      {showCreateModal && (
        <CreateTeamModal
          onClose={() => setShowCreateModal(false)}
          onCreate={handleCreateTeam}
          loading={hook.loading}
        />
      )}
    </div>
  );
};
