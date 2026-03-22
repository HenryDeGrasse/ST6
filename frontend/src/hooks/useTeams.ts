/**
 * Hook for team CRUD operations.
 *
 * Fetches the user's teams and provides create / update / delete operations.
 */
import { useState, useCallback } from "react";
import type {
  Team,
  TeamDetailResponse,
  TeamMember,
  CreateTeamRequest,
  UpdateTeamRequest,
  AddTeamMemberRequest,
  ApiErrorResponse,
} from "@weekly-commitments/contracts";
import { useApiClient } from "../api/ApiContext.js";

export interface UseTeamsResult {
  teams: Team[];
  loading: boolean;
  error: string | null;
  fetchTeams: () => Promise<void>;
  fetchTeamDetail: (teamId: string) => Promise<TeamDetailResponse | null>;
  createTeam: (req: CreateTeamRequest) => Promise<Team | null>;
  updateTeam: (teamId: string, req: UpdateTeamRequest) => Promise<Team | null>;
  addMember: (teamId: string, req: AddTeamMemberRequest) => Promise<TeamMember | null>;
  removeMember: (teamId: string, userId: string) => Promise<boolean>;
  clearError: () => void;
}

export function useTeams(): UseTeamsResult {
  const client = useApiClient();
  const [teams, setTeams] = useState<Team[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const clearError = useCallback(() => setError(null), []);

  const extractError = useCallback((resp: { error?: unknown; response: Response }): string => {
    const err = resp.error as ApiErrorResponse | undefined;
    if (err?.error?.message) return err.error.message;
    return `Request failed (${String(resp.response.status)})`;
  }, []);

  const fetchTeams = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const resp = await client.GET("/teams");
      if (resp.data) {
        const data = resp.data as { teams: Team[] };
        setTeams(data.teams);
      } else {
        setError(extractError(resp));
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : "Network error");
    } finally {
      setLoading(false);
    }
  }, [client, extractError]);

  const fetchTeamDetail = useCallback(
    async (teamId: string): Promise<TeamDetailResponse | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.GET("/teams/{teamId}", {
          params: { path: { teamId } },
        });
        if (resp.data) {
          return resp.data as TeamDetailResponse;
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

  const createTeam = useCallback(
    async (req: CreateTeamRequest): Promise<Team | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/teams", {
          body: req,
        });
        if (resp.data) {
          const created = resp.data as Team;
          setTeams((prev) => [...prev, created]);
          return created;
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

  const updateTeam = useCallback(
    async (teamId: string, req: UpdateTeamRequest): Promise<Team | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.PATCH("/teams/{teamId}", {
          params: { path: { teamId } },
          body: req,
        });
        if (resp.data) {
          const updated = resp.data as Team;
          setTeams((prev) => prev.map((t) => (t.id === teamId ? updated : t)));
          return updated;
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

  const addMember = useCallback(
    async (teamId: string, req: AddTeamMemberRequest): Promise<TeamMember | null> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.POST("/teams/{teamId}/members", {
          params: { path: { teamId } },
          body: req,
        });
        if (resp.data) {
          return resp.data as TeamMember;
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

  const removeMember = useCallback(
    async (teamId: string, userId: string): Promise<boolean> => {
      setLoading(true);
      setError(null);
      try {
        const resp = await client.DELETE("/teams/{teamId}/members/{userId}", {
          params: { path: { teamId, userId } },
        });
        if (resp.response.ok) {
          return true;
        }
        setError(extractError(resp));
        return false;
      } catch (e) {
        setError(e instanceof Error ? e.message : "Network error");
        return false;
      } finally {
        setLoading(false);
      }
    },
    [client, extractError],
  );

  return {
    teams,
    loading,
    error,
    fetchTeams,
    fetchTeamDetail,
    createTeam,
    updateTeam,
    addMember,
    removeMember,
    clearError,
  };
}
