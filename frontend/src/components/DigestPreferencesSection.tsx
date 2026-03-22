import React, { useCallback, useEffect, useState } from "react";
import type { ApiErrorResponse, OrgPolicy, UpdateDigestConfigRequest } from "@weekly-commitments/contracts";
import { useOptionalApiClient } from "../api/ApiContext.js";
import { useOptionalAuth } from "../context/AuthContext.js";
import styles from "./DigestPreferencesSection.module.css";

const DIGEST_DAY_OPTIONS = [
  "MONDAY",
  "TUESDAY",
  "WEDNESDAY",
  "THURSDAY",
  "FRIDAY",
  "SATURDAY",
  "SUNDAY",
] as const;

interface DigestConfigState {
  digestDay: string;
  digestTime: string;
}

const DEFAULT_CONFIG: DigestConfigState = {
  digestDay: "FRIDAY",
  digestTime: "17:00",
};

/**
 * Admin-only configuration surface for the weekly digest schedule.
 *
 * The rest of the org policy remains backend-managed for now; this section only
 * exposes the Wave 3 digest timing fields required by the roadmap step.
 */
export const DigestPreferencesSection: React.FC = () => {
  const client = useOptionalApiClient();
  const auth = useOptionalAuth();
  const isAdmin = auth?.user.roles.includes("ADMIN") ?? false;

  const [config, setConfig] = useState<DigestConfigState>(DEFAULT_CONFIG);
  const [savedConfig, setSavedConfig] = useState<DigestConfigState>(DEFAULT_CONFIG);
  const [loading, setLoading] = useState(isAdmin);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as ApiErrorResponse | undefined;
    if (err?.error?.message) {
      return err.error.message;
    }
    return `Request failed (${String(resp.response.status)})`;
  }, []);

  const loadPolicy = useCallback(async () => {
    if (!isAdmin || !client) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const resp = await client.GET("/admin/org-policy");
      if (resp.data) {
        const policy = resp.data as OrgPolicy;
        const nextConfig = {
          digestDay: policy.digestDay,
          digestTime: policy.digestTime,
        };
        setConfig(nextConfig);
        setSavedConfig(nextConfig);
        return;
      }

      setError(extractError(resp));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [client, extractError, isAdmin]);

  useEffect(() => {
    void loadPolicy();
  }, [loadPolicy]);

  const isDirty = config.digestDay !== savedConfig.digestDay || config.digestTime !== savedConfig.digestTime;

  const handleSave = useCallback(async () => {
    if (!client) {
      return;
    }

    const request: UpdateDigestConfigRequest = {
      digestDay: config.digestDay,
      digestTime: config.digestTime,
    };

    setSaving(true);
    setError(null);
    setSuccessMessage(null);

    try {
      const resp = await client.PATCH("/admin/org-policy/digest", {
        body: request,
      });

      if (resp.data) {
        const updatedPolicy = resp.data as OrgPolicy;
        const nextConfig = {
          digestDay: updatedPolicy.digestDay,
          digestTime: updatedPolicy.digestTime,
        };
        setConfig(nextConfig);
        setSavedConfig(nextConfig);
        setSuccessMessage("Weekly digest schedule saved.");
        return;
      }

      setError(extractError(resp));
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setSaving(false);
    }
  }, [client, config.digestDay, config.digestTime, extractError]);

  if (!isAdmin || !client) {
    return null;
  }

  return (
    <section data-testid="digest-preferences-section" className={styles.section} aria-labelledby="digest-preferences-title">
      <div className={styles.header}>
        <div>
          <h3 id="digest-preferences-title" className={styles.title}>
            Weekly Digest Preferences
          </h3>
        </div>
        <p className={styles.description}>
          Choose when managers receive the weekly summary notification for their teams.
        </p>
      </div>

      {error && (
        <div data-testid="digest-preferences-error" role="alert" className={styles.error}>
          {error}
        </div>
      )}

      <div className={styles.fields}>
        <label className={styles.field}>
          <span className={styles.label}>Digest day</span>
          <select
            data-testid="digest-day-select"
            value={config.digestDay}
            onChange={(e) => {
              setConfig((current) => ({ ...current, digestDay: e.target.value }));
              setSuccessMessage(null);
            }}
            disabled={loading || saving}
            className={styles.select}
          >
            {DIGEST_DAY_OPTIONS.map((day) => (
              <option key={day} value={day}>
                {day.charAt(0) + day.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </label>

        <label className={styles.field}>
          <span className={styles.label}>Digest time</span>
          <input
            data-testid="digest-time-input"
            type="time"
            step={900}
            value={config.digestTime}
            onChange={(e) => {
              setConfig((current) => ({ ...current, digestTime: e.target.value }));
              setSuccessMessage(null);
            }}
            disabled={loading || saving}
            className={styles.input}
          />
        </label>
      </div>

      <div className={styles.actions}>
        <button
          type="button"
          data-testid="save-digest-preferences-btn"
          onClick={() => {
            void handleSave();
          }}
          disabled={loading || saving || !isDirty}
          className={styles.saveButton}
        >
          {loading ? "Loading schedule…" : saving ? "Saving…" : "Save digest schedule"}
        </button>

        <button
          type="button"
          data-testid="reload-digest-preferences-btn"
          onClick={() => {
            setSuccessMessage(null);
            void loadPolicy();
          }}
          disabled={loading || saving}
          className={styles.secondaryButton}
        >
          Reload
        </button>

        {successMessage && (
          <span data-testid="digest-preferences-success" role="status" aria-live="polite" className={styles.success}>
            {successMessage}
          </span>
        )}
      </div>
    </section>
  );
};
