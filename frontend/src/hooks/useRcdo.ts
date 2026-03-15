/**
 * Hook for RCDO hierarchy data (tree browsing + search).
 */
import { useState, useCallback } from "react";
import type {
  RcdoCry,
  RcdoSearchResult,
  ApiErrorResponse,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface UseRcdoResult {
  tree: RcdoCry[];
  searchResults: RcdoSearchResult[];
  loading: boolean;
  error: string | null;
  fetchTree: () => Promise<void>;
  search: (query: string) => Promise<void>;
  clearSearch: () => void;
  clearError: () => void;
}

export function useRcdo(): UseRcdoResult {
  const client = useApiClient();
  const [tree, setTree] = useState<RcdoCry[]>([]);
  const [searchResults, setSearchResults] = useState<RcdoSearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => {
    setError(null);
  }, []);

  const extractError = useCallback(
    (resp: { error?: unknown; response: Response }): string => {
      const err = resp.error as ApiErrorResponse | undefined;
      if (err?.error?.message) return err.error.message;
      return `Request failed (${String(resp.response.status)})`;
    },
    [],
  );

  const fetchTree = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await client.GET("/rcdo/tree");
      if (resp.data) {
        const data = resp.data as { rallyCries: RcdoCry[] };
        setTree(data.rallyCries);
      } else {
        setError(extractError(resp));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [client, extractError]);

  const search = useCallback(
    async (query: string) => {
      if (query.length < 2) {
        setSearchResults([]);
        return;
      }
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/rcdo/search", {
          params: { query: { q: query } },
        });
        if (resp.data) {
          const data = resp.data as { results: RcdoSearchResult[] };
          setSearchResults(data.results);
        } else {
          setError(extractError(resp));
        }
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  const clearSearch = useCallback(() => {
    setSearchResults([]);
  }, []);

  return { tree, searchResults, loading, error, fetchTree, search, clearSearch, clearError };
}
