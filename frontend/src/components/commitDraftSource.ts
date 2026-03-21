import type { CommitSourceType } from "@weekly-commitments/contracts";

/** Prefix used to encode draft-source in a commit's tags array. */
export const DRAFT_SOURCE_PREFIX = "draft_source:";

/** Visual source badges shown for prefilled and manually-added commits. */
export type CommitDraftSource = CommitSourceType | "NEW";

export const COMMIT_DRAFT_SOURCE_LABELS: Record<CommitDraftSource, string> = {
  CARRIED_FORWARD: "🔄 Carried forward",
  RECURRING: "📋 Recurring",
  COVERAGE_GAP: "🎯 Coverage gap",
  NEW: "✏️ New",
};

/**
 * Extracts the draft source from a commit's tags.
 *
 * - Known `draft_source:*` tags map to their corresponding AI-prefill source.
 * - No `draft_source:*` tag means the commit was added manually in the editor.
 * - Carry-forward copies keep their existing carried-forward badge and are not relabelled as `NEW`.
 * - Unknown `draft_source:*` values return null so we do not mislabel bad data.
 */
export function getCommitDraftSource(tags: string[], carriedFromCommitId: string | null = null): CommitDraftSource | null {
  let sawDraftSourceTag = false;

  for (const tag of tags) {
    if (!tag.startsWith(DRAFT_SOURCE_PREFIX)) {
      continue;
    }

    sawDraftSourceTag = true;
    const raw = tag.slice(DRAFT_SOURCE_PREFIX.length);
    if (raw in COMMIT_DRAFT_SOURCE_LABELS && raw !== "NEW") {
      return raw as CommitSourceType;
    }
  }

  if (sawDraftSourceTag || carriedFromCommitId) {
    return null;
  }

  return "NEW";
}
