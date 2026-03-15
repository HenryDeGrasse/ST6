/**
 * Hook for manager review actions (approve, request changes).
 */
import { useState, useCallback } from "react";
import type {
  ManagerReview,
  ReviewDecision,
  ApiErrorResponse,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface UseReviewResult {
  reviews: ManagerReview[];
  loading: boolean;
  error: string | null;
  submitReview: (planId: string, decision: ReviewDecision, comments: string) => Promise<ManagerReview | null>;
  clearError: () => void;
}

export function useReview(): UseReviewResult {
  const client = useApiClient();
  const [reviews, setReviews] = useState<ManagerReview[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const extractError = useCallback(
    (resp: { error?: unknown; response: Response }): string => {
      const err = resp.error as ApiErrorResponse | undefined;
      if (err?.error?.message) return err.error.message;
      return `Request failed (${String(resp.response.status)})`;
    },
    [],
  );

  const submitReview = useCallback(
    async (planId: string, decision: ReviewDecision, comments: string): Promise<ManagerReview | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/plans/{planId}/review", {
          params: { path: { planId } },
          body: { decision, comments },
        });
        if (resp.data) {
          const review = resp.data as ManagerReview;
          setReviews((prev) => [...prev, review]);
          return review;
        }
        setError(extractError(resp));
        return null;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return null;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  return { reviews, loading, error, submitReview, clearError };
}
